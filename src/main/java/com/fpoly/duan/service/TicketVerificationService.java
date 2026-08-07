package com.fpoly.duan.service;

import com.fpoly.duan.dto.TicketQrVerificationDTO;
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.entity.Showtime;
import com.fpoly.duan.entity.Ticket;
import com.fpoly.duan.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TicketVerificationService {
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final TicketRepository ticketRepository;
    private final TicketQrService ticketQrService;

    /** Quét QR = soát vé luôn trong một bước: đánh dấu khách đã vào rạp ngay khi quét lần đầu hợp lệ. */
    public TicketQrVerificationDTO verify(Staff staff, String qrToken) {
        Resolved r = resolve(staff, qrToken);
        List<Ticket> group = sameShowtimeGroup(r.ticket(), r.orderTickets(), r.showtime());

        LocalDateTime existing = earliestCheckedInAt(group);
        if (existing != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vé đã được sử dụng lúc " + existing.format(DISPLAY_FORMAT));
        }

        LocalDateTime now = LocalDateTime.now();
        group.forEach(t -> {
            t.setCheckedIn(true);
            t.setCheckedInAt(now);
        });
        ticketRepository.saveAll(group);

        return buildDto(r.ticket(), r.showtime(), group, true, now);
    }

    private Resolved resolve(Staff staff, String qrToken) {
        TicketQrService.TicketReference ref = ticketQrService.decodeReference(qrToken);
        Ticket ticket = ticketRepository.findById(ref.ticketId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy vé"));
        if (ticket.getQrToken() == null || !MessageDigest.isEqual(
                ticket.getQrToken().getBytes(StandardCharsets.UTF_8), qrToken.getBytes(StandardCharsets.UTF_8))
                || !ref.ticketCode().equals(ticket.getTicketCode()) || !ticketQrService.matchesExpectedCode(ticket)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR vé không hợp lệ hoặc đã bị thay đổi");
        }
        Showtime st = ticket.getShowtime();
        if (st == null || st.getRoom() == null || st.getRoom().getCinema() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vé thiếu thông tin rạp");
        }
        if (staff.getCinema() != null && staff.getCinema().getCinemaId() != null
                && !staff.getCinema().getCinemaId().equals(st.getRoom().getCinema().getCinemaId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vé không thuộc rạp bạn đang làm việc");
        }
        if (ticket.getStatus() == null || ticket.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vé chưa thanh toán hoặc đã bị hủy");
        }
        List<Ticket> orderTickets = ticket.getOrderOnline() != null
                ? ticketRepository.findByOrderOnline_OrderOnlineId(ticket.getOrderOnline().getOrderOnlineId())
                : List.of(ticket);
        return new Resolved(ticket, st, orderTickets);
    }

    /** Vé cùng đơn + cùng suất chiếu với vé vừa quét — nhóm được soát vé chung một lượt. */
    private List<Ticket> sameShowtimeGroup(Ticket ticket, List<Ticket> orderTickets, Showtime showtime) {
        List<Ticket> group = orderTickets.stream()
                .filter(t -> t.getShowtime() != null
                        && t.getShowtime().getShowtimeId() != null
                        && t.getShowtime().getShowtimeId().equals(showtime.getShowtimeId()))
                .toList();
        return group.isEmpty() ? List.of(ticket) : group;
    }

    private LocalDateTime earliestCheckedInAt(List<Ticket> group) {
        return group.stream()
                .filter(t -> Boolean.TRUE.equals(t.getCheckedIn()))
                .map(Ticket::getCheckedInAt)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private TicketQrVerificationDTO buildDto(Ticket ticket, Showtime st, List<Ticket> group,
                                              boolean checkedIn, LocalDateTime checkedInAt) {
        String seats = group.stream()
                .filter(t -> t.getSeat() != null)
                .sorted(Comparator
                        .comparing((Ticket t) -> string(t.getSeat().getRow()))
                        .thenComparing(t -> t.getSeat().getX() != null ? t.getSeat().getX() : 0)
                        .thenComparing(t -> string(t.getSeat().getNumber())))
                .map(t -> string(t.getSeat().getRow()) + string(t.getSeat().getNumber()))
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElseGet(() -> ticket.getSeat() == null ? "" : string(ticket.getSeat().getRow()) + string(ticket.getSeat().getNumber()));
        return TicketQrVerificationDTO.builder()
                .orderCode(ticket.getOrderOnline() != null ? ticket.getOrderOnline().getOrderCode() : null)
                .ticketCode(ticket.getTicketCode())
                .status(ticket.getStatus())
                .customerName(ticket.getOrderOnline() != null && ticket.getOrderOnline().getUser() != null
                        ? ticket.getOrderOnline().getUser().getFullname() : "Khách vãng lai")
                .cinemaName(st.getRoom().getCinema().getName())
                .cinemaAddress(st.getRoom().getCinema().getAddress())
                .movieTitle(st.getMovie() != null ? st.getMovie().getTitle() : "Vé xem phim")
                .showtime(st.getStartTime() != null ? st.getStartTime().format(DISPLAY_FORMAT) : "")
                .roomName(st.getRoom().getName())
                .seatNumber(seats)
                .checkedIn(checkedIn)
                .checkedInAt(checkedInAt != null ? checkedInAt.format(DISPLAY_FORMAT) : null)
                .build();
    }

    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }

    private record Resolved(Ticket ticket, Showtime showtime, List<Ticket> orderTickets) {}
}
