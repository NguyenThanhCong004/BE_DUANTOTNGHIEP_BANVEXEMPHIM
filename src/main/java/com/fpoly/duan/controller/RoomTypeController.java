package com.fpoly.duan.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.RoomTypeDTO;
import com.fpoly.duan.entity.RoomType;
import com.fpoly.duan.repository.RoomTypeRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/room-types")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "9. Loại phòng chiếu (Room Types)", description = "Danh sách loại phòng (read-only, seed trực tiếp DB).")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class RoomTypeController {

    private final RoomTypeRepository roomTypeRepository;

    @GetMapping
    @Operation(summary = "Danh sách loại phòng chiếu")
    public ResponseEntity<ApiResponse<List<RoomTypeDTO>>> getRoomTypes() {
        List<RoomTypeDTO> data = roomTypeRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.<List<RoomTypeDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách loại phòng thành công")
                .data(data)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết loại phòng chiếu")
    public ResponseEntity<ApiResponse<RoomTypeDTO>> getRoomTypeById(@PathVariable Integer id) {
        RoomType rt = roomTypeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy loại phòng với id: " + id));
        return ResponseEntity.ok(ApiResponse.<RoomTypeDTO>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(toDTO(rt))
                .build());
    }

    private RoomTypeDTO toDTO(RoomType rt) {
        return RoomTypeDTO.builder()
                .roomTypeId(rt.getRoomTypeId())
                .name(rt.getName())
                .standardSeatCount(rt.getStandardSeatCount())
                .vipSeatCount(rt.getVipSeatCount())
                .coupleSeatCount(rt.getCoupleSeatCount())
                .build();
    }
}
