package com.fpoly.duan.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
import com.fpoly.duan.entity.Seat;
import com.fpoly.duan.entity.SeatType;
import com.fpoly.duan.repository.RoomRepository;
import com.fpoly.duan.repository.SeatRepository;
import com.fpoly.duan.repository.SeatTypeRepository;

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

    @GetMapping("/seat-types/{id}")
    @Operation(summary = "Chi tiết loại ghế", tags = { "Table: seat_types" })
    public ResponseEntity<ApiResponse<SeatTypeDTO>> getSeatTypeById(@PathVariable Integer id) {
        SeatType t = seatTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại ghế với id: " + id));
        SeatTypeDTO dto = SeatTypeDTO.builder()
                .seatTypeId(t.getSeatTypeId())
                .name(t.getName())
                .surcharge(t.getSurcharge())
                .build();
        return ResponseEntity.ok(ApiResponse.<SeatTypeDTO>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(dto)
                .build());
    }

    @PostMapping("/seat-types")
    @Operation(summary = "Tạo loại ghế", tags = { "Table: seat_types" })
    public ResponseEntity<ApiResponse<SeatTypeDTO>> createSeatType(@RequestBody SeatTypeDTO dto) {
        if (dto == null || dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new RuntimeException("Tên loại ghế không được để trống");
        }
        if (seatTypeRepository.findByName(dto.getName().trim()).isPresent()) {
            throw new RuntimeException("Tên loại ghế đã tồn tại");
        }
        SeatType t = new SeatType();
        t.setName(dto.getName().trim());
        t.setSurcharge(dto.getSurcharge() != null ? dto.getSurcharge() : 0.0);
        SeatType saved = seatTypeRepository.save(t);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<SeatTypeDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo loại ghế thành công")
                .data(SeatTypeDTO.builder()
                        .seatTypeId(saved.getSeatTypeId())
                        .name(saved.getName())
                        .surcharge(saved.getSurcharge())
                        .build())
                .build());
    }

    @PutMapping("/seat-types/{id}")
    @Operation(summary = "Cập nhật loại ghế", tags = { "Table: seat_types" })
    public ResponseEntity<ApiResponse<SeatTypeDTO>> updateSeatType(@PathVariable Integer id,
            @RequestBody SeatTypeDTO dto) {
        SeatType t = seatTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy loại ghế với id: " + id));
        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            String name = dto.getName().trim();
            Optional<SeatType> dup = seatTypeRepository.findByName(name);
            if (dup.isPresent() && !dup.get().getSeatTypeId().equals(id)) {
                throw new RuntimeException("Tên loại ghế đã tồn tại");
            }
            t.setName(name);
        }
        if (dto.getSurcharge() != null) {
            t.setSurcharge(dto.getSurcharge());
        }
        SeatType saved = seatTypeRepository.save(t);
        return ResponseEntity.ok(ApiResponse.<SeatTypeDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật loại ghế thành công")
                .data(SeatTypeDTO.builder()
                        .seatTypeId(saved.getSeatTypeId())
                        .name(saved.getName())
                        .surcharge(saved.getSurcharge())
                        .build())
                .build());
    }

    @DeleteMapping("/seat-types/{id}")
    @Operation(summary = "Xóa loại ghế", tags = { "Table: seat_types" })
    public ResponseEntity<ApiResponse<Void>> deleteSeatType(@PathVariable Integer id) {
        if (!seatTypeRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy loại ghế với id: " + id);
        }
        long used = seatRepository.countBySeatType_SeatTypeId(id);
        if (used > 0) {
            throw new RuntimeException("Không thể xóa: còn " + used + " ghế đang dùng loại này");
        }
        seatTypeRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa loại ghế thành công")
                .build());
    }

    @GetMapping("/seat-types")
    @Operation(summary = "Danh sách loại ghế", tags = { "Table: seat_types" })
    public ResponseEntity<ApiResponse<List<SeatTypeDTO>>> getSeatTypes() {
        List<SeatTypeDTO> data = seatTypeRepository.findAll().stream()
                .map(t -> SeatTypeDTO.builder()
                        .seatTypeId(t.getSeatTypeId())
                        .name(t.getName())
                        .surcharge(t.getSurcharge())
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
    @Operation(summary = "Lưu / đồng bộ sơ đồ ghế phòng", description = "Xóa ghế cũ của phòng rồi tạo lại theo payload.", tags = {
            "Table: seats" })
    public ResponseEntity<ApiResponse<Void>> saveSeatLayout(@RequestBody SeatLayoutRequest request) {
        if (request == null || request.getRoomId() == null) {
            throw new RuntimeException("roomId không hợp lệ");
        }
        Room room = roomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng với id: " + request.getRoomId()));

        // Sync đơn giản: xóa toàn bộ ghế rồi tạo lại theo payload
        seatRepository.deleteByRoom_RoomId(request.getRoomId());

        if (request.getSeats() == null || request.getSeats().isEmpty()) {
            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .status(HttpStatus.OK.value())
                    .message("Cập nhật sơ đồ ghế thành công")
                    .build());
        }

        List<Seat> created = request.getSeats().stream()
                .map(this::toSeatEntity)
                .peek(s -> s.setRoom(room))
                .collect(Collectors.toList());

        seatRepository.saveAll(created);

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
                .seatTypeSurcharge(st != null && st.getSurcharge() != null ? st.getSurcharge() : 0.0)
                .status(s.getStatus() != null ? s.getStatus() : "available")
                .build();
    }

    private Seat toSeatEntity(SeatLayoutItem item) {
        if (item == null) {
            throw new RuntimeException("Seat item không hợp lệ");
        }
        if (item.getX() == null || item.getY() == null) {
            throw new RuntimeException("Thiếu x/y cho ghế");
        }
        if (item.getSeatTypeName() == null || item.getSeatTypeName().trim().isEmpty()) {
            throw new RuntimeException("Thiếu loại ghế");
        }

        SeatType seatType = seatTypeRepository.findByName(item.getSeatTypeName())
                .orElseGet(() -> {
                    // Allow FE to create seat layout even if seed data for seat types is missing.
                    SeatType created = new SeatType();
                    created.setName(item.getSeatTypeName());
                    created.setSurcharge(0.0);
                    return seatTypeRepository.save(created);
                });

        Seat s = new Seat();
        s.setX(item.getX());
        s.setY(item.getY());
        s.setRow(item.getRow());
        s.setNumber(item.getNumber());
        s.setSeatType(seatType);
        return s;
    }
}

