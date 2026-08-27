package com.fpoly.duan.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.SeatDTO;
import com.fpoly.duan.dto.SeatLayoutItem;
import com.fpoly.duan.dto.SeatLayoutRequest;
import com.fpoly.duan.dto.SeatTypeDTO;
import com.fpoly.duan.entity.Room;
import com.fpoly.duan.entity.RoomType;
import com.fpoly.duan.entity.Seat;
import com.fpoly.duan.entity.SeatType;
import com.fpoly.duan.entity.Ticket;
import com.fpoly.duan.repository.RoomRepository;
import com.fpoly.duan.repository.SeatRepository;
import com.fpoly.duan.repository.SeatTypeRepository;
import com.fpoly.duan.repository.TicketRepository;
import com.fpoly.duan.service.CinemaScopeService;
import com.fpoly.duan.util.SearchUtils;
import com.fpoly.duan.util.SeatLabel;
import com.fpoly.duan.util.SeatTypeNaming;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Table: seats", description = "CRUD và layout ghế theo phòng (bảng seats).")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class SeatController {
    private final SeatRepository seatRepository;
    private final SeatTypeRepository seatTypeRepository;
    private final RoomRepository roomRepository;
    private final TicketRepository ticketRepository;
    private final CinemaScopeService cinemaScopeService;

    @GetMapping("/seat-types/{id}")
    @Operation(summary = "Chi tiết loại ghế", tags = { "Table: seat_types" })
    public ResponseEntity<ApiResponse<SeatTypeDTO>> getSeatTypeById(@PathVariable Integer id) {
        SeatType t = seatTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại ghế với id: " + id));
        SeatTypeDTO dto = SeatTypeDTO.builder()
                .seatTypeId(t.getSeatTypeId())
                .name(t.getName())
                .surcharge(t.getSurcharge())
                .coupleSeat(Boolean.TRUE.equals(t.getCoupleSeat()))
                .color(t.getColor())
                .build();
        return ResponseEntity.ok(ApiResponse.<SeatTypeDTO>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(dto)
                .build());
    }

    @PutMapping("/seat-types/{id}")
    @Operation(summary = "Cập nhật loại ghế", tags = { "Table: seat_types" })
    public ResponseEntity<ApiResponse<SeatTypeDTO>> updateSeatType(@PathVariable Integer id,
            @RequestBody SeatTypeDTO dto) {
        SeatType t = seatTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại ghế với id: " + id));
        
        boolean hasChanges = false;
        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            String name = dto.getName().trim();
            if (!name.equals(t.getName())) {
                Optional<SeatType> dup = seatTypeRepository.findByName(name);
                if (dup.isPresent() && !dup.get().getSeatTypeId().equals(id)) {
                    throw new RuntimeException("Tên loại ghế đã tồn tại");
                }
                t.setName(name);
                hasChanges = true;
            }
        }
        if (dto.getSurcharge() != null && !dto.getSurcharge().equals(t.getSurcharge())) {
            t.setSurcharge(dto.getSurcharge());
            hasChanges = true;
        }
        if (dto.getCoupleSeat() != null && !dto.getCoupleSeat().equals(t.getCoupleSeat())) {
            t.setCoupleSeat(dto.getCoupleSeat());
            hasChanges = true;
        }
        if (dto.getColor() != null) {
            String newColor = SeatTypeNaming.normalizeColorHex(dto.getColor());
            if (!newColor.equalsIgnoreCase(t.getColor())) {
                t.setColor(newColor);
                hasChanges = true;
            }
        }

        if (!hasChanges) {
            return ResponseEntity.ok(ApiResponse.<SeatTypeDTO>builder()
                    .status(HttpStatus.OK.value())
                    .message("Không có thay đổi để cập nhật")
                    .data(SeatTypeDTO.builder()
                            .seatTypeId(t.getSeatTypeId())
                            .name(t.getName())
                            .surcharge(t.getSurcharge())
                            .coupleSeat(Boolean.TRUE.equals(t.getCoupleSeat()))
                            .color(t.getColor())
                            .build())
                    .build());
        }

        SeatType saved = seatTypeRepository.save(t);
        return ResponseEntity.ok(ApiResponse.<SeatTypeDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật loại ghế thành công")
                .data(SeatTypeDTO.builder()
                        .seatTypeId(saved.getSeatTypeId())
                        .name(saved.getName())
                        .surcharge(saved.getSurcharge())
                        .coupleSeat(Boolean.TRUE.equals(saved.getCoupleSeat()))
                        .color(saved.getColor())
                        .build())
                .build());
    }

    @GetMapping("/seat-types")
    @Operation(summary = "Danh sách loại ghế", tags = { "Table: seat_types" })
    public ResponseEntity<ApiResponse<List<SeatTypeDTO>>> getSeatTypes(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q) {
        String term = SearchUtils.pick(search, keyword, q);
        List<SeatTypeDTO> data = seatTypeRepository.findAll().stream()
                .filter(t -> SearchUtils.matches(term, t.getSeatTypeId(), t.getName(), t.getColor(), t.getSurcharge(), t.getCoupleSeat()))
                .map(t -> SeatTypeDTO.builder()
                        .seatTypeId(t.getSeatTypeId())
                        .name(t.getName())
                        .surcharge(t.getSurcharge())
                        .coupleSeat(Boolean.TRUE.equals(t.getCoupleSeat()))
                        .color(t.getColor())
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.<List<SeatTypeDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách loại ghế thành công")
                .data(data)
                .build());
    }

    @PutMapping("/seats/{id}/status")
    @Operation(summary = "Cập nhật trạng thái ghế", description = "Cập nhật trạng thái ghế: available, locked, maintenance", tags = { "Table: seats" })
    public ResponseEntity<ApiResponse<Void>> updateSeatStatus(
            @PathVariable Integer id,
            @RequestBody Map<String, String> request) {
        
        String status = request.get("status");
        if (status == null || (!status.equals("available") && !status.equals("locked") && !status.equals("maintenance"))) {
            throw new RuntimeException("Trạng thái không hợp lệ. Phải là: available, locked, maintenance");
        }
        
        Seat seat = seatRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy ghế với id: " + id));
        if (seat.getRoom() != null) {
            cinemaScopeService.requireCinemaAccess(seat.getRoom().getCinema());
            cinemaScopeService.requireCinemaOperational(seat.getRoom().getCinema());
        }
        
        seat.setStatus(status);
        seatRepository.save(seat);
        
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật trạng thái ghế thành công")
                .build());
    }

    @GetMapping("/seats")
    @Operation(summary = "Danh sách ghế theo phòng", tags = { "Table: seats" })
    public ResponseEntity<ApiResponse<List<SeatDTO>>> getSeats(
            @Parameter(description = "ID phòng chiếu", required = true) @RequestParam Integer roomId) {
        List<Seat> seats = seatRepository.findByRoom_RoomId(roomId);

        List<SeatDTO> data = seats.stream().map(this::toSeatDTO).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.<List<SeatDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách ghế thành công")
                .data(data)
                .build());
    }

    @PutMapping("/seats")
    @Transactional
    @Operation(summary = "Lưu / đồng bộ sơ đồ ghế phòng", description = "Cập nhật theo tọa độ (x,y): giữ seat_id khi trùng ô — tránh lỗi FK khi đã có vé. Chỉ xóa ghế bị bỏ khỏi sơ đồ nếu không còn vé.", tags = {
            "Table: seats" })
    public ResponseEntity<ApiResponse<Void>> saveSeatLayout(@RequestBody SeatLayoutRequest request) {
        if (request == null || request.getRoomId() == null) {
            throw new RuntimeException("roomId không hợp lệ");
        }
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng với id: " + request.getRoomId()));
        cinemaScopeService.requireCinemaAccess(room.getCinema());
        cinemaScopeService.requireCinemaOperational(room.getCinema());

        List<Seat> existing = seatRepository.findByRoom_RoomId(request.getRoomId());
        Map<String, Seat> byXY = existing.stream()
                .collect(Collectors.toMap(s -> s.getX() + "," + s.getY(), s -> s, (a, b) -> a));

        if (request.getSeats() == null || request.getSeats().isEmpty()) {
            for (Seat old : new ArrayList<>(existing)) {
                if (seatHasOngoingOrFutureShowtime(old.getSeatId())) {
                    throw new RuntimeException(
                            "Không thể xóa hết sơ đồ: ghế " + SeatLabel.of(old)
                                    + " đang có vé ở suất chiếu hiện tại hoặc sắp tới. Xử lý/dời các suất đó trước.");
                }
                detachSeatFromTickets(old);
                seatRepository.delete(old);
            }
            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .status(HttpStatus.OK.value())
                    .message("Cập nhật sơ đồ ghế thành công")
                    .build());
        }

        Map<String, SeatLayoutItem> wanted = new LinkedHashMap<>();
        for (SeatLayoutItem item : request.getSeats()) {
            if (item == null || item.getX() == null || item.getY() == null) {
                throw new RuntimeException("Thiếu x/y cho ghế");
            }
            if (item.getSeatTypeName() == null || item.getSeatTypeName().trim().isEmpty()) {
                throw new RuntimeException("Thiếu loại ghế");
            }
            String key = item.getX() + "," + item.getY();
            wanted.put(key, item);
        }

        if (room.getRoomType() != null) {
            Map<String, Integer> counts = new HashMap<>();
            counts.put("standard", 0);
            counts.put("vip", 0);
            counts.put("couple", 0);
            for (SeatLayoutItem item : wanted.values()) {
                SeatType st = resolveSeatType(item.getSeatTypeName());
                String cat;
                if (Boolean.TRUE.equals(st.getCoupleSeat())) {
                    cat = "couple";
                } else if (st.getName() != null && st.getName().toLowerCase().contains("vip")) {
                    cat = "vip";
                } else {
                    cat = "standard";
                }
                counts.merge(cat, 1, Integer::sum);
            }
            RoomType rt = room.getRoomType();
            int stdGot = counts.get("standard");
            int vipGot = counts.get("vip");
            int coupleGot = counts.get("couple");
            if (stdGot != rt.getStandardSeatCount() || vipGot != rt.getVipSeatCount() || coupleGot != rt.getCoupleSeatCount()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        String.format("Sơ đồ ghế chưa đúng theo loại phòng \"%s\": cần %d ghế thường, %d ghế VIP, %d ghế đôi. Hiện có: %d thường, %d VIP, %d đôi.",
                                rt.getName(),
                                rt.getStandardSeatCount(), rt.getVipSeatCount(), rt.getCoupleSeatCount(),
                                stdGot, vipGot, coupleGot));
            }
        }

        List<Seat> toPersist = new ArrayList<>();
        for (SeatLayoutItem item : wanted.values()) {
            String key = item.getX() + "," + item.getY();
            Seat seat = byXY.get(key);
            if (seat == null) {
                seat = new Seat();
                seat.setRoom(room);
                seat.setStatus("available");
            }
            if (item.getStatus() != null) {
                seat.setStatus(item.getStatus());
            }
            SeatType seatType = resolveSeatType(item.getSeatTypeName());
            seat.setX(item.getX());
            seat.setY(item.getY());
            seat.setRow(item.getRow());
            seat.setNumber(item.getNumber());
            seat.setSeatType(seatType);
            toPersist.add(seat);
        }

        Set<String> payloadKeys = wanted.keySet();
        for (Seat old : new ArrayList<>(existing)) {
            String key = old.getX() + "," + old.getY();
            if (payloadKeys.contains(key)) {
                continue;
            }
            if (seatHasOngoingOrFutureShowtime(old.getSeatId())) {
                throw new RuntimeException(
                        "Không thể bỏ ghế " + SeatLabel.of(old) + " tại ô (" + old.getX() + "," + old.getY()
                                + "): đang có vé ở suất chiếu hiện tại hoặc sắp tới. Xử lý/dời các suất đó trước.");
            }
            detachSeatFromTickets(old);
            seatRepository.delete(old);
        }

        seatRepository.saveAll(toPersist);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật sơ đồ ghế thành công")
                .build());
    }

    private SeatDTO toSeatDTO(Seat s) {
        SeatType st = s.getSeatType();
        return SeatDTO.builder()
                .seatId(s.getSeatId())
                .x(s.getX())
                .y(s.getY())
                .row(s.getRow())
                .number(s.getNumber())
                .seatTypeName(st != null ? st.getName() : null)
                .coupleSeat(st != null && Boolean.TRUE.equals(st.getCoupleSeat()))
                .seatTypeColor(st != null ? st.getColor() : null)
                .seatTypeSurcharge(st != null && st.getSurcharge() != null ? st.getSurcharge() : 0.0)
                .status(s.getStatus() != null ? s.getStatus() : "available")
                .build();
    }

    /** true nếu ghế còn vé chưa hủy ở suất chiếu đang diễn ra hoặc sắp tới (chưa kết thúc) —
     * chặn xóa ghế trong trường hợp này. Vé của suất đã kết thúc (quá khứ) KHÔNG chặn xóa. */
    private boolean seatHasOngoingOrFutureShowtime(Integer seatId) {
        LocalDateTime now = LocalDateTime.now();
        for (Ticket t : ticketRepository.findActiveTicketsBySeatId(seatId)) {
            var showtime = t.getShowtime();
            if (showtime == null || showtime.getStartTime() == null) continue;
            int durationMin = showtime.getMovie() != null && showtime.getMovie().getDuration() != null
                    ? showtime.getMovie().getDuration() : 120;
            if (showtime.getStartTime().plusMinutes(durationMin).isAfter(now)) {
                return true;
            }
        }
        return false;
    }

    /** Gỡ ghế khỏi mọi vé còn tham chiếu (kể cả vé đã hủy/suất đã qua) trước khi xóa ghế —
     * tránh vi phạm khóa ngoại. seat_label đã được snapshot từ lúc gắn ghế nên hóa đơn cũ
     * vẫn hiển thị đúng tên ghế dù seat_id bị gán NULL. */
    private void detachSeatFromTickets(Seat seat) {
        List<Ticket> tickets = ticketRepository.findBySeat_SeatId(seat.getSeatId());
        if (tickets.isEmpty()) return;
        for (Ticket t : tickets) {
            if (t.getSeatLabel() == null) {
                t.setSeatLabel(SeatLabel.of(seat));
            }
            t.setSeat(null);
        }
        ticketRepository.saveAll(tickets);
    }

    /** Chỉ tra cứu — không tự tạo loại ghế mới. Danh sách loại ghế đã cố định (3 loại), quản lý qua trang Loại ghế. */
    private SeatType resolveSeatType(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new RuntimeException("Thiếu loại ghế");
        }
        String name = rawName.trim();
        return seatTypeRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException(
                        "Loại ghế \"" + name + "\" không tồn tại. Vui lòng chọn 1 trong các loại ghế đã có."));
    }

}

