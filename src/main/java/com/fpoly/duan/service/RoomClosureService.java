package com.fpoly.duan.service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fpoly.duan.dto.AffectedOrderDTO;
import com.fpoly.duan.dto.AffectedTicketDTO;
import com.fpoly.duan.dto.AlternateShowtimeSuggestionDTO;
import com.fpoly.duan.dto.AutoMoveResultDTO;
import com.fpoly.duan.dto.PlannedSeatAssignmentDTO;
import com.fpoly.duan.dto.TicketRescheduleSnapshotDTO;
import com.fpoly.duan.entity.Movie;
import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.entity.Room;
import com.fpoly.duan.entity.Seat;
import com.fpoly.duan.entity.Showtime;
import com.fpoly.duan.entity.Ticket;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.repository.OrderOnlineRepository;
import com.fpoly.duan.repository.RoomRepository;
import com.fpoly.duan.repository.SeatRepository;
import com.fpoly.duan.repository.ShowtimeRepository;
import com.fpoly.duan.repository.TicketRepository;
import com.fpoly.duan.util.SeatLabel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Đóng phòng chiếu tạm thời (hỏng ghế/sự cố kỹ thuật) và dời các vé đã bán ở suất "Sắp chiếu" của
 * phòng đó sang suất chiếu khác. Chỉ admin thao tác thủ công (đóng/mở, xác nhận từng đơn dời) —
 * hệ thống chỉ tự tìm suất gợi ý, không tự động dời hàng loạt.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoomClosureService {

    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final long PENDING_SEAT_HOLD_MINUTES = 5;
    private static final int MAX_SUGGESTIONS = 5;
    private static final int ROOM_STATUS_CLOSED = 2;
    private static final int ROOM_STATUS_ACTIVE = 1;
    private static final int ORDER_STATUS_CANCELLED = 2;
    private static final int TICKET_STATUS_CANCELLED = 2;

    private final RoomRepository roomRepository;
    private final ShowtimeRepository showtimeRepository;
    private final SeatRepository seatRepository;
    private final TicketRepository ticketRepository;
    private final OrderOnlineRepository orderOnlineRepository;
    private final TicketQrService ticketQrService;
    private final TicketEmailService ticketEmailService;
    private final CinemaScopeService cinemaScopeService;

    @Transactional
    public Room closeRoom(Integer roomId, String reason) {
        Room room = requireRoom(roomId);
        cinemaScopeService.requireCinemaAccess(room.getCinema());
        room.setStatus(ROOM_STATUS_CLOSED);
        room.setCloseReason(reason);
        room.setClosedAt(nowInAppZone());
        return roomRepository.save(room);
    }

    /** @return số đơn còn tồn đọng (vé chưa được dời/hủy) tại thời điểm mở lại — FE hiển thị cảnh báo nếu > 0. */
    @Transactional
    public int reopenRoom(Integer roomId) {
        Room room = requireRoom(roomId);
        cinemaScopeService.requireCinemaAccess(room.getCinema());
        int remaining = listAffectedOrders(roomId).size();
        room.setStatus(ROOM_STATUS_ACTIVE);
        room.setCloseReason(null);
        room.setClosedAt(null);
        roomRepository.save(room);
        return remaining;
    }

    @Transactional(readOnly = true)
    public List<AffectedOrderDTO> listAffectedOrders(Integer roomId) {
        Room room = requireRoom(roomId);
        cinemaScopeService.requireCinemaAccess(room.getCinema());

        List<Ticket> tickets = ticketRepository.findActiveTicketsInRoomForFutureShowtimes(roomId, nowInAppZone());
        Map<Integer, List<Ticket>> byOrder = new LinkedHashMap<>();
        for (Ticket t : tickets) {
            if (t.getOrderOnline() == null) {
                continue;
            }
            byOrder.computeIfAbsent(t.getOrderOnline().getOrderOnlineId(), k -> new ArrayList<>()).add(t);
        }

        // Suất chiếu sớm nhất của mỗi đơn lên đầu — admin dễ ưu tiên xử lý những suất sắp diễn ra.
        List<Map.Entry<Integer, List<Ticket>>> sortedEntries = byOrder.entrySet().stream()
                .sorted(Comparator.comparing(e -> earliestShowtimeStart(e.getValue())))
                .collect(Collectors.toList());

        List<AffectedOrderDTO> result = new ArrayList<>();
        for (Map.Entry<Integer, List<Ticket>> entry : sortedEntries) {
            List<Ticket> group = entry.getValue();
            OrderOnline order = group.get(0).getOrderOnline();
            User user = order.getUser();
            result.add(AffectedOrderDTO.builder()
                    .orderOnlineId(order.getOrderOnlineId())
                    .orderCode(order.getOrderCode())
                    .customerName(user != null ? user.getFullname() : "Khách vãng lai (bán tại quầy)")
                    .customerEmail(user != null ? user.getEmail() : null)
                    .customerPhone(user != null ? user.getPhone() : null)
                    .finalAmount(order.getFinalAmount())
                    .tickets(group.stream().map(this::toAffectedTicketDTO).collect(Collectors.toList()))
                    .build());
        }
        return result;
    }

    private LocalDateTime earliestShowtimeStart(List<Ticket> tickets) {
        return tickets.stream()
                .map(t -> t.getShowtime() != null ? t.getShowtime().getStartTime() : null)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(LocalDateTime.MAX);
    }

    @Transactional(readOnly = true)
    public List<AlternateShowtimeSuggestionDTO> suggestAlternateShowtimes(Integer roomId, Integer orderId) {
        List<Ticket> orderTickets = affectedTicketsOfOrder(roomId, orderId);
        Showtime originalShowtime = orderTickets.get(0).getShowtime();
        Movie movie = originalShowtime.getMovie();
        Room originalRoom = originalShowtime.getRoom();
        if (movie == null || originalRoom == null || originalRoom.getCinema() == null) {
            return List.of();
        }

        List<Showtime> candidates = showtimeRepository
                .findByMovie_MovieIdAndRoom_Cinema_CinemaId(movie.getMovieId(), originalRoom.getCinema().getCinemaId())
                .stream()
                .filter(s -> !s.getShowtimeId().equals(originalShowtime.getShowtimeId()))
                // Chỉ gợi ý suất TRỄ HƠN suất khách đã đặt — không đề xuất giờ sớm hơn (khách có thể không kịp đến).
                .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(originalShowtime.getStartTime()))
                .filter(s -> cinemaScopeService.isRoomOperational(s.getRoom()))
                .sorted(Comparator.comparing(Showtime::getStartTime))
                .collect(Collectors.toList());

        List<AlternateShowtimeSuggestionDTO> results = new ArrayList<>();
        for (Showtime candidate : candidates) {
            if (results.size() >= MAX_SUGGESTIONS) {
                break;
            }
            List<PlannedSeatAssignmentDTO> plan = tryPlanSeats(candidate, orderTickets);
            if (plan != null) {
                results.add(AlternateShowtimeSuggestionDTO.builder()
                        .showtimeId(candidate.getShowtimeId())
                        .movieTitle(movie.getTitle())
                        .roomId(candidate.getRoom().getRoomId())
                        .roomName(candidate.getRoom().getName())
                        .startTime(TIME_FORMAT.format(candidate.getStartTime()))
                        .plannedSeats(plan)
                        .build());
            }
        }
        return results;
    }

    @Transactional
    public void confirmMove(Integer roomId, Integer orderId, Integer targetShowtimeId) {
        List<Ticket> orderTickets = affectedTicketsOfOrder(roomId, orderId);
        OrderOnline order = orderTickets.get(0).getOrderOnline();

        Showtime target = showtimeRepository.findByIdForUpdate(targetShowtimeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy suất chiếu thay thế"));
        cinemaScopeService.requireRoomOperational(target.getRoom());

        List<PlannedSeatAssignmentDTO> plan = tryPlanSeats(target, orderTickets);
        if (plan == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Suất chiếu này không còn đủ ghế phù hợp (có thể vừa có người khác đặt) — vui lòng chọn suất khác");
        }

        List<TicketRescheduleSnapshotDTO> oldSnapshots = orderTickets.stream()
                .map(this::toSnapshot)
                .collect(Collectors.toList());

        Map<Integer, PlannedSeatAssignmentDTO> planByTicketId = plan.stream()
                .collect(Collectors.toMap(PlannedSeatAssignmentDTO::getTicketId, p -> p));
        Map<Integer, Seat> seatById = seatRepository
                .findAllByIdWithType(plan.stream().map(PlannedSeatAssignmentDTO::getSeatId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Seat::getSeatId, s -> s));

        for (Ticket ticket : orderTickets) {
            PlannedSeatAssignmentDTO assignment = planByTicketId.get(ticket.getTicketId());
            ticket.setShowtime(target);
            Seat newSeat = seatById.get(assignment.getSeatId());
            ticket.setSeat(newSeat);
            ticket.setSeatLabel(SeatLabel.of(newSeat));
            // Giữ nguyên price/originalPrice — khách được trả lại đúng loại ghế đã mua, không thu thêm/hoàn chênh lệch.
        }
        ticketRepository.saveAll(orderTickets);
        // QR/mã vé cũ gắn chết suất+ghế cũ — bắt buộc sinh lại sau khi đổi, nếu không vé sẽ không quét được nữa.
        ticketQrService.assignSecureCodes(orderTickets);
        ticketRepository.saveAll(orderTickets);

        order.setRescheduledAt(nowInAppZone());
        orderOnlineRepository.save(order);

        ticketEmailService.sendTicketRescheduledEmail(order, orderTickets, oldSnapshots);
    }

    @Transactional
    public void cancelForClosure(Integer roomId, Integer orderId) {
        List<Ticket> orderTickets = affectedTicketsOfOrder(roomId, orderId);
        OrderOnline order = orderTickets.get(0).getOrderOnline();

        List<TicketRescheduleSnapshotDTO> oldSnapshots = orderTickets.stream()
                .map(this::toSnapshot)
                .collect(Collectors.toList());

        Room closedRoom = orderTickets.get(0).getShowtime().getRoom();
        String cancelReason = "Đóng phòng bảo trì"
                + (closedRoom.getCloseReason() != null && !closedRoom.getCloseReason().isBlank()
                        ? ": " + closedRoom.getCloseReason() : "");
        for (Ticket ticket : orderTickets) {
            ticket.setStatus(TICKET_STATUS_CANCELLED);
            ticket.setCancelReason(cancelReason);
        }
        ticketRepository.saveAll(orderTickets);

        order.setStatus(ORDER_STATUS_CANCELLED);
        order.setRescheduledAt(nowInAppZone());
        orderOnlineRepository.save(order);

        ticketEmailService.sendOrderCancelledForClosureEmail(order, oldSnapshots);
    }

    /**
     * Admin chủ động bấm nút (không tự động khi đóng phòng): dời hàng loạt các suất chiếu "Sắp
     * chiếu" của phòng đang đóng, trong khoảng [bây giờ, windowEnd] (windowEnd null = không giới
     * hạn), sang phòng khác cùng rạp + cùng Loại phòng chiếu đang trống đúng khung giờ đó — giữ
     * nguyên phim/giờ chiếu, chỉ đổi phòng + ghế (ghế map theo đúng loại thường/VIP/đôi). Suất nào
     * không tìm được phòng phù hợp thì giữ nguyên ở phòng cũ, lộ ra ở {@link #listAffectedOrders}
     * để admin xử lý thủ công như luồng cũ.
     */
    @Transactional
    public AutoMoveResultDTO autoMoveMatchingRoomShowtimes(Integer roomId, LocalDateTime windowEnd) {
        Room room = requireRoom(roomId);
        cinemaScopeService.requireCinemaAccess(room.getCinema());

        if (room.getRoomType() == null) {
            return AutoMoveResultDTO.builder()
                    .movedCount(0).unresolvedCount(0)
                    .summary("Phòng này chưa được gán Loại phòng chiếu nên không thể tự tìm phòng cùng loại — vui lòng xử lý thủ công từng đơn.")
                    .build();
        }

        LocalDateTime now = nowInAppZone();
        List<Showtime> affected = showtimeRepository.findByRoom_RoomId(roomId).stream()
                .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(now))
                .filter(s -> windowEnd == null || !s.getStartTime().isAfter(windowEnd))
                .sorted(Comparator.comparing(Showtime::getStartTime))
                .collect(Collectors.toList());
        if (affected.isEmpty()) {
            return AutoMoveResultDTO.builder().movedCount(0).unresolvedCount(0)
                    .summary("Không có suất chiếu nào trong khoảng thời gian đã chọn.").build();
        }

        Map<Integer, List<Ticket>> ticketsByShowtime = ticketRepository
                .findActiveTicketsInRoomForFutureShowtimes(roomId, now).stream()
                .filter(t -> t.getShowtime() != null)
                .collect(Collectors.groupingBy(t -> t.getShowtime().getShowtimeId()));

        List<Room> sameTypeRooms = roomRepository.findByCinema_CinemaId(room.getCinema().getCinemaId()).stream()
                .filter(r -> !r.getRoomId().equals(roomId))
                .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                .filter(r -> r.getRoomType() != null
                        && r.getRoomType().getRoomTypeId().equals(room.getRoomType().getRoomTypeId()))
                .collect(Collectors.toList());

        int movedCount = 0;
        List<String> movedRoomNames = new ArrayList<>();
        List<Ticket> allMovedTickets = new ArrayList<>();
        Map<Integer, List<TicketRescheduleSnapshotDTO>> oldSnapshotsByOrder = new LinkedHashMap<>();
        int unresolvedCount = 0;

        for (Showtime s : affected) {
            List<Ticket> tickets = ticketsByShowtime.getOrDefault(s.getShowtimeId(), List.of());

            Room target = null;
            List<PlannedSeatAssignmentDTO> plan = null;
            for (Room candidate : sameTypeRooms) {
                if (overlaps(candidate, s)) continue;
                List<PlannedSeatAssignmentDTO> candidatePlan = planSeatsForEmptyRoom(candidate, tickets);
                if (candidatePlan != null) {
                    target = candidate;
                    plan = candidatePlan;
                    break;
                }
            }

            if (target == null) {
                unresolvedCount++;
                continue;
            }

            // Chụp lại suất/ghế TRƯỚC khi đổi — dùng cho email báo khách.
            List<TicketRescheduleSnapshotDTO> snapshotsForThisShowtime = tickets.stream()
                    .map(this::toSnapshot).collect(Collectors.toList());

            s.setRoom(target);
            showtimeRepository.saveAndFlush(s);

            Map<Integer, PlannedSeatAssignmentDTO> planByTicketId = plan.stream()
                    .collect(Collectors.toMap(PlannedSeatAssignmentDTO::getTicketId, p -> p));
            Map<Integer, Seat> seatById = seatRepository
                    .findAllByIdWithType(plan.stream().map(PlannedSeatAssignmentDTO::getSeatId).collect(Collectors.toSet()))
                    .stream().collect(Collectors.toMap(Seat::getSeatId, x -> x));
            for (Ticket t : tickets) {
                PlannedSeatAssignmentDTO assignment = planByTicketId.get(t.getTicketId());
                Seat newSeat = seatById.get(assignment.getSeatId());
                t.setSeat(newSeat);
                t.setSeatLabel(SeatLabel.of(newSeat));
            }
            ticketRepository.saveAll(tickets);

            for (int i = 0; i < tickets.size(); i++) {
                Ticket t = tickets.get(i);
                if (t.getOrderOnline() == null) continue;
                oldSnapshotsByOrder
                        .computeIfAbsent(t.getOrderOnline().getOrderOnlineId(), k -> new ArrayList<>())
                        .add(snapshotsForThisShowtime.get(i));
            }
            allMovedTickets.addAll(tickets);
            movedCount++;
            movedRoomNames.add(target.getName());
        }

        if (!allMovedTickets.isEmpty()) {
            // QR/mã vé cũ gắn chết theo ghế — bắt buộc sinh lại sau khi đổi ghế.
            ticketQrService.assignSecureCodes(allMovedTickets);
            ticketRepository.saveAll(allMovedTickets);

            Map<Integer, List<Ticket>> movedByOrder = allMovedTickets.stream()
                    .filter(t -> t.getOrderOnline() != null)
                    .collect(Collectors.groupingBy(t -> t.getOrderOnline().getOrderOnlineId()));
            for (Map.Entry<Integer, List<Ticket>> entry : movedByOrder.entrySet()) {
                List<Ticket> orderTickets = entry.getValue();
                OrderOnline order = orderTickets.get(0).getOrderOnline();
                order.setRescheduledAt(now);
                orderOnlineRepository.save(order);
                ticketEmailService.sendTicketRescheduledEmail(order, orderTickets,
                        oldSnapshotsByOrder.getOrDefault(entry.getKey(), List.of()));
            }
        }

        String summary;
        if (movedCount == 0) {
            summary = "Không tìm được phòng trống cùng loại phù hợp cho " + unresolvedCount
                    + " suất chiếu — vui lòng xử lý thủ công từng đơn.";
        } else {
            String roomsStr = movedRoomNames.stream().distinct().collect(Collectors.joining(", "));
            summary = "Đã dời " + movedCount + " suất chiếu sang phòng " + roomsStr
                    + (unresolvedCount == 0 ? "." : "; còn " + unresolvedCount + " suất chưa tìm được phòng, cần xử lý thủ công.");
        }

        return AutoMoveResultDTO.builder()
                .movedCount(movedCount)
                .unresolvedCount(unresolvedCount)
                .summary(summary)
                .build();
    }

    /** Xếp ghế cho toàn bộ {@code tickets} vào phòng trống {@code candidateRoom} (đúng loại ghế, first-fit); null nếu không đủ hoặc gây ghế mồ côi. */
    private List<PlannedSeatAssignmentDTO> planSeatsForEmptyRoom(Room candidateRoom, List<Ticket> tickets) {
        if (tickets.isEmpty()) {
            return List.of();
        }
        List<Seat> allSeats = seatRepository.findByRoom_RoomId(candidateRoom.getRoomId());
        Map<Integer, Deque<Seat>> availableByType = allSeats.stream()
                .filter(s -> !"maintenance".equals(s.getStatus()) && !"locked".equals(s.getStatus()))
                .filter(s -> s.getSeatType() != null)
                .sorted(Comparator.<Seat, String>comparing(s -> s.getRow() != null ? s.getRow() : "")
                        .thenComparing(s -> s.getX() != null ? s.getX() : 0))
                .collect(Collectors.groupingBy(s -> s.getSeatType().getSeatTypeId(),
                        Collectors.toCollection(ArrayDeque::new)));

        List<PlannedSeatAssignmentDTO> assignments = new ArrayList<>();
        Set<Integer> plannedSeatIds = new HashSet<>();
        for (Ticket ticket : tickets) {
            Seat oldSeat = ticket.getSeat();
            if (oldSeat == null || oldSeat.getSeatType() == null) {
                return null;
            }
            Deque<Seat> pool = availableByType.get(oldSeat.getSeatType().getSeatTypeId());
            Seat assigned = pool != null ? pool.pollFirst() : null;
            if (assigned == null) {
                return null;
            }
            plannedSeatIds.add(assigned.getSeatId());
            assignments.add(PlannedSeatAssignmentDTO.builder()
                    .ticketId(ticket.getTicketId())
                    .seatId(assigned.getSeatId())
                    .seatLabel(safe(assigned.getRow()) + safe(assigned.getNumber()))
                    .seatTypeName(assigned.getSeatType() != null ? assigned.getSeatType().getName() : null)
                    .build());
        }

        try {
            SeatLayoutRules.assertNoNewSingleSeatOrphanInRows(allSeats, Set.of(), plannedSeatIds);
        } catch (ResponseStatusException e) {
            return null;
        }
        return assignments;
    }

    /** true nếu {@code candidateRoom} đã có suất chiếu khác trùng khung giờ với {@code showtime}. */
    private boolean overlaps(Room candidateRoom, Showtime showtime) {
        Movie movie = showtime.getMovie();
        int durationMin = movie != null && movie.getDuration() != null ? movie.getDuration() : 120;
        LocalDateTime start = showtime.getStartTime();
        LocalDateTime newEnd = start.plusMinutes(durationMin);
        for (Showtime ot : showtimeRepository.findByRoom_RoomId(candidateRoom.getRoomId())) {
            if (ot.getShowtimeId().equals(showtime.getShowtimeId())) continue;
            if (ot.getStartTime() == null) continue;
            Movie om = ot.getMovie();
            int od = om != null && om.getDuration() != null ? om.getDuration() : 120;
            LocalDateTime otEnd = ot.getStartTime().plusMinutes(od);
            if (start.isBefore(otEnd) && ot.getStartTime().isBefore(newEnd)) {
                return true;
            }
        }
        return false;
    }

    /** Vé còn hoạt động (chưa hủy) của đơn, thuộc đúng phòng {@code roomId} đang đóng — 404 nếu đơn không khớp phòng. */
    private List<Ticket> affectedTicketsOfOrder(Integer roomId, Integer orderId) {
        Room room = requireRoom(roomId);
        cinemaScopeService.requireCinemaAccess(room.getCinema());

        List<Ticket> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(orderId).stream()
                .filter(t -> t.getStatus() == null || t.getStatus() != TICKET_STATUS_CANCELLED)
                .filter(t -> t.getShowtime() != null && t.getShowtime().getRoom() != null
                        && roomId.equals(t.getShowtime().getRoom().getRoomId()))
                .collect(Collectors.toList());
        if (tickets.isEmpty() || tickets.get(0).getOrderOnline() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Không tìm thấy đơn hàng còn vé đang hoạt động thuộc phòng này");
        }
        return tickets;
    }

    /** Thử gán mỗi vé của đơn sang 1 ghế cùng loại còn trống ở suất ứng viên; null nếu không đủ hoặc gây ghế mồ côi mới. */
    private List<PlannedSeatAssignmentDTO> tryPlanSeats(Showtime candidate, List<Ticket> orderTickets) {
        Integer roomId = candidate.getRoom() != null ? candidate.getRoom().getRoomId() : null;
        if (roomId == null) {
            return null;
        }
        List<Seat> allSeats = seatRepository.findByRoom_RoomId(roomId);
        LocalDateTime pendingSince = nowInAppZone().minusMinutes(PENDING_SEAT_HOLD_MINUTES);
        Set<Integer> heldSeatIds = new HashSet<>(
                ticketRepository.findHeldSeatIdsByShowtime(candidate.getShowtimeId(), pendingSince));

        Map<Integer, Deque<Seat>> availableByType = allSeats.stream()
                .filter(s -> s.getSeatId() != null && !heldSeatIds.contains(s.getSeatId()))
                .filter(s -> !"maintenance".equals(s.getStatus()) && !"locked".equals(s.getStatus()))
                .filter(s -> s.getSeatType() != null)
                .sorted(Comparator.<Seat, String>comparing(s -> s.getRow() != null ? s.getRow() : "")
                        .thenComparing(s -> s.getX() != null ? s.getX() : 0))
                .collect(Collectors.groupingBy(s -> s.getSeatType().getSeatTypeId(),
                        Collectors.toCollection(ArrayDeque::new)));

        List<PlannedSeatAssignmentDTO> assignments = new ArrayList<>();
        Set<Integer> plannedSeatIds = new HashSet<>();
        for (Ticket ticket : orderTickets) {
            Seat oldSeat = ticket.getSeat();
            if (oldSeat == null || oldSeat.getSeatType() == null) {
                return null;
            }
            Deque<Seat> pool = availableByType.get(oldSeat.getSeatType().getSeatTypeId());
            Seat assigned = pool != null ? pool.pollFirst() : null;
            if (assigned == null) {
                return null;
            }
            plannedSeatIds.add(assigned.getSeatId());
            assignments.add(PlannedSeatAssignmentDTO.builder()
                    .ticketId(ticket.getTicketId())
                    .seatId(assigned.getSeatId())
                    .seatLabel(safe(assigned.getRow()) + safe(assigned.getNumber()))
                    .seatTypeName(assigned.getSeatType() != null ? assigned.getSeatType().getName() : null)
                    .build());
        }

        try {
            SeatLayoutRules.assertNoNewSingleSeatOrphanInRows(allSeats, heldSeatIds, plannedSeatIds);
        } catch (ResponseStatusException e) {
            return null;
        }
        return assignments;
    }

    private AffectedTicketDTO toAffectedTicketDTO(Ticket t) {
        Showtime st = t.getShowtime();
        Seat seat = t.getSeat();
        return AffectedTicketDTO.builder()
                .ticketId(t.getTicketId())
                .movieTitle(st != null && st.getMovie() != null ? st.getMovie().getTitle() : null)
                .showtimeId(st != null ? st.getShowtimeId() : null)
                .showtimeStart(st != null && st.getStartTime() != null ? TIME_FORMAT.format(st.getStartTime()) : null)
                .seatLabel(seat != null ? safe(seat.getRow()) + safe(seat.getNumber()) : null)
                .seatTypeId(seat != null && seat.getSeatType() != null ? seat.getSeatType().getSeatTypeId() : null)
                .seatTypeName(seat != null && seat.getSeatType() != null ? seat.getSeatType().getName() : null)
                .build();
    }

    private TicketRescheduleSnapshotDTO toSnapshot(Ticket t) {
        Showtime st = t.getShowtime();
        Seat seat = t.getSeat();
        return TicketRescheduleSnapshotDTO.builder()
                .ticketId(t.getTicketId())
                .movieTitle(st != null && st.getMovie() != null ? st.getMovie().getTitle() : null)
                .seatLabel(seat != null ? safe(seat.getRow()) + safe(seat.getNumber()) : null)
                .showtimeStart(st != null && st.getStartTime() != null ? TIME_FORMAT.format(st.getStartTime()) : null)
                .build();
    }

    private Room requireRoom(Integer roomId) {
        return roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng với id: " + roomId));
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static LocalDateTime nowInAppZone() {
        return LocalDateTime.now(APP_ZONE);
    }
}
