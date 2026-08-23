package com.fpoly.duan.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.CinemaDTO;
import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.entity.Room;
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.repository.CinemaRepository;
import com.fpoly.duan.repository.RoomRepository;
import com.fpoly.duan.security.CustomUserDetails;
import com.fpoly.duan.service.AuditLogService;
import com.fpoly.duan.util.SearchUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/cinemas")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "1b. Rạp (Cinemas)", description = "Danh sách rạp — FE Super Admin chọn rạp.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
@SuppressWarnings("null")
// [SUPER ADMIN ONLY] - This section belongs to Super Admin. Do not modify without authorization.
public class CinemaController {

    private static final List<Integer> VALID_STATUSES = List.of(0, 1, 2);

    private final CinemaRepository cinemaRepository;
    private final RoomRepository roomRepository;
    private final AuditLogService auditLogService;
    private final com.fpoly.duan.repository.StaffRepository staffRepository;

    @GetMapping
    @Operation(summary = "Danh sách rạp")
    public ResponseEntity<ApiResponse<List<CinemaDTO>>> list(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q) {
        String term = SearchUtils.pick(search, keyword, q);
        boolean canSeeLocked = isSuperAdmin(authentication);
        List<CinemaDTO> data = cinemaRepository.findAll().stream()
                .filter(c -> canSeeLocked || isOperational(c))
                .filter(c -> SearchUtils.matches(term, c.getCinemaId(), c.getName(), c.getAddress(), c.getStatus()))
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.<List<CinemaDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách rạp thành công")
                .data(data)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết rạp")
    public ResponseEntity<ApiResponse<CinemaDTO>> getById(Authentication authentication, @PathVariable Integer id) {
        Cinema c = cinemaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy rạp với id: " + id));
        if (!isSuperAdmin(authentication) && !isOperational(c)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy rạp với id: " + id);
        }
        return ResponseEntity.ok(ApiResponse.<CinemaDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy thông tin rạp thành công")
                .data(toDTO(c))
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo rạp")
    public ResponseEntity<ApiResponse<CinemaDTO>> create(@Valid @RequestBody CinemaDTO dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên rạp không được để trống");
        }

        String name = dto.getName().trim();
        String address = dto.getAddress() != null ? dto.getAddress().trim() : "";

        if (cinemaRepository.existsByNameIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên rạp '" + name + "' đã tồn tại");
        }
        if (!address.isEmpty() && cinemaRepository.existsByAddressIgnoreCase(address)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Địa chỉ '" + address + "' đã được sử dụng bởi rạp khác");
        }

        int status = dto.getStatus() != null ? dto.getStatus() : 1;
        if (!VALID_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Trạng thái không hợp lệ. Chỉ chấp nhận: 0 (tạm ngưng), 1 (hoạt động), 2 (sắp khai trương)");
        }

        Cinema c = new Cinema();
        c.setName(name);
        c.setAddress(address);
        c.setStatus(status);
        Cinema saved = cinemaRepository.save(c);
        auditLogService.log(currentActorStaff(), "CREATE_CINEMA", "CINEMA", saved.getCinemaId(),
                "Tạo rạp \"" + saved.getName() + "\"");
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<CinemaDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo rạp thành công")
                .data(toDTO(saved))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật rạp")
    public ResponseEntity<ApiResponse<CinemaDTO>> update(@PathVariable Integer id, @RequestBody CinemaDTO dto) {
        Cinema c = cinemaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy rạp với id: " + id));

        boolean hasChanges = false;

        if (dto.getName() != null) {
            String name = dto.getName().trim();
            if (name.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên rạp không được để trống");
            }
            if (cinemaRepository.existsByNameIgnoreCaseAndCinemaIdNot(name, id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên rạp '" + name + "' đã tồn tại ở chi nhánh khác");
            }
            c.setName(name);
            hasChanges = true;
        }

        if (dto.getAddress() != null) {
            String address = dto.getAddress().trim();
            if (!address.isEmpty() && cinemaRepository.existsByAddressIgnoreCaseAndCinemaIdNot(address, id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Địa chỉ '" + address + "' đã được sử dụng bởi rạp khác");
            }
            c.setAddress(address);
            hasChanges = true;
        }

        boolean cinemaLockedNow = false;
        if (dto.getStatus() != null) {
            if (!VALID_STATUSES.contains(dto.getStatus())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Trạng thái không hợp lệ. Chỉ chấp nhận: 0 (tạm ngưng), 1 (hoạt động), 2 (sắp khai trương)");
            }
            cinemaLockedNow = dto.getStatus() != 1;
            c.setStatus(dto.getStatus());
            hasChanges = true;
        }

        if (!hasChanges) {
            return ResponseEntity.ok(ApiResponse.<CinemaDTO>builder()
                    .status(HttpStatus.OK.value())
                    .message("Không có thay đổi để cập nhật")
                    .data(toDTO(c))
                    .build());
        }

        Cinema saved = cinemaRepository.save(c);
        auditLogService.log(currentActorStaff(), "UPDATE_CINEMA", "CINEMA", saved.getCinemaId(),
                "Cập nhật rạp \"" + saved.getName() + "\"");

        // Rạp vừa bị khóa/tạm ngưng: thu hồi ngay phiên đăng nhập hiện tại của toàn bộ nhân
        // viên thuộc rạp (đổi session_version — tái dùng cơ chế giới hạn 1 thiết bị) để họ
        // không thể tiếp tục thao tác cho tới khi Super Admin mở lại rạp và họ đăng nhập lại.
        if (cinemaLockedNow) {
            List<Staff> staffOfCinema = staffRepository.findByCinema_CinemaId(id);
            for (Staff s : staffOfCinema) {
                s.setSessionVersion(java.util.UUID.randomUUID().toString());
            }
            staffRepository.saveAll(staffOfCinema);
        }
        return ResponseEntity.ok(ApiResponse.<CinemaDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật rạp thành công")
                .data(toDTO(saved))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa rạp")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        Cinema cinema = cinemaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy rạp với id: " + id));

        List<Room> roomsInCinema = roomRepository.findByCinema_CinemaId(id);
        if (!roomsInCinema.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Không thể xóa rạp này vì đang có " + roomsInCinema.size()
                    + " phòng chiếu. Vui lòng xóa hoặc chuyển phòng chiếu sang rạp khác trước.");
        }

        if (staffRepository.existsByCinema_CinemaId(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Không thể xóa rạp này vì đang có nhân viên được gắn với rạp. Vui lòng chuyển hoặc xóa nhân viên trước.");
        }

        cinemaRepository.deleteById(id);
        auditLogService.log(currentActorStaff(), "DELETE_CINEMA", "CINEMA", id,
                "Xóa rạp \"" + cinema.getName() + "\"");
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa rạp thành công")
                .build());
    }

    private Staff currentActorStaff() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserDetails details) {
            return details.getStaff();
        }
        return null;
    }

    private CinemaDTO toDTO(Cinema c) {
        return CinemaDTO.builder()
                .cinemaId(c.getCinemaId())
                .name(c.getName())
                .address(c.getAddress())
                .status(c.getStatus())
                .build();
    }

    private boolean isOperational(Cinema c) {
        return c != null && (c.getStatus() == null || c.getStatus() == 1);
    }

    private boolean isSuperAdmin(Authentication authentication) {
        return authentication != null
                && authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch("ROLE_SUPER_ADMIN"::equals);
    }
}
