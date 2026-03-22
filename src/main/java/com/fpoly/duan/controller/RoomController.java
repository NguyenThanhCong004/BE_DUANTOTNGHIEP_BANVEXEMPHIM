package com.fpoly.duan.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.RoomDTO;
import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.entity.Room;
import com.fpoly.duan.repository.RoomRepository;
import com.fpoly.duan.repository.CinemaRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "8. Phòng chiếu (Rooms)", description = "CRUD phòng theo rạp (`cinemaId`).")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class RoomController {
    private final RoomRepository roomRepository;
    private final CinemaRepository cinemaRepository;

    @GetMapping
    @Operation(summary = "Danh sách phòng")
    public ResponseEntity<ApiResponse<List<RoomDTO>>> getRooms(
            @Parameter(description = "Lọc theo rạp") @RequestParam(required = false) Integer cinemaId) {
        List<Room> rooms = cinemaId == null ? roomRepository.findAll() : roomRepository.findByCinema_CinemaId(cinemaId);
        List<RoomDTO> dto = rooms.stream().map(this::toDTO).collect(Collectors.toList());

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.<List<RoomDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách phòng thành công")
                .data(dto)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết phòng")
    public ResponseEntity<ApiResponse<RoomDTO>> getRoomById(@PathVariable Integer id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng với id: " + id));

        return ResponseEntity.ok(ApiResponse.<RoomDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy thông tin phòng thành công")
                .data(toDTO(room))
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo phòng")
    public ResponseEntity<ApiResponse<RoomDTO>> createRoom(@RequestBody RoomDTO roomDTO) {
        if (roomDTO == null) {
            throw new RuntimeException("Dữ liệu phòng không hợp lệ");
        }
        if (roomDTO.getCinemaId() == null) {
            throw new RuntimeException("Vui lòng chọn rạp (cinemaId)");
        }
        if (roomDTO.getName() == null || roomDTO.getName().trim().isEmpty()) {
            throw new RuntimeException("Tên phòng không được để trống");
        }

        Cinema cinema = cinemaRepository.findById(roomDTO.getCinemaId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy rạp với mã: " + roomDTO.getCinemaId()));

        Room room = new Room();
        room.setName(roomDTO.getName());
        room.setStatus(roomDTO.getStatus() != null ? roomDTO.getStatus() : 1);
        room.setCinema(cinema);

        Room created = roomRepository.save(room);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<RoomDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo phòng thành công")
                .data(toDTO(created))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật phòng")
    public ResponseEntity<ApiResponse<RoomDTO>> updateRoom(@PathVariable Integer id, @RequestBody RoomDTO roomDTO) {
        if (roomDTO == null) {
            throw new RuntimeException("Dữ liệu phòng không hợp lệ");
        }

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng với id: " + id));

        if (roomDTO.getName() != null && !roomDTO.getName().trim().isEmpty()) {
            room.setName(roomDTO.getName());
        }
        if (roomDTO.getStatus() != null) {
            room.setStatus(roomDTO.getStatus());
        }

        // Nếu FE gửi cinemaId thì cập nhật luôn
        if (roomDTO.getCinemaId() != null) {
            Cinema cinema = cinemaRepository.findById(roomDTO.getCinemaId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy rạp với mã: " + roomDTO.getCinemaId()));
            room.setCinema(cinema);
        }

        Room updated = roomRepository.save(room);
        return ResponseEntity.ok(ApiResponse.<RoomDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật phòng thành công")
                .data(toDTO(updated))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa phòng")
    public ResponseEntity<ApiResponse<Void>> deleteRoom(@PathVariable Integer id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng với id: " + id));

        roomRepository.delete(room);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa phòng thành công")
                .build());
    }

    private RoomDTO toDTO(Room r) {
        Cinema c = r.getCinema();
        return RoomDTO.builder()
                .id(r.getRoomId())
                .name(r.getName())
                .status(r.getStatus())
                .cinemaId(c != null ? c.getCinemaId() : null)
                .build();
    }
}

