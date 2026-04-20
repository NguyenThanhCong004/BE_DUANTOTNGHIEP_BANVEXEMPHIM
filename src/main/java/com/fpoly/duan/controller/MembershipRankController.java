package com.fpoly.duan.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.MembershipRankDTO;
import com.fpoly.duan.entity.MembershipRank;
import com.fpoly.duan.repository.MembershipRankRepository;
import com.fpoly.duan.repository.UserRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/membership-ranks")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "8d. Hạng hội viên", description = "CRUD membership rank — Super Admin.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
// [SUPER ADMIN ONLY] - This section belongs to Super Admin. Do not modify without authorization.
public class MembershipRankController {

    private final MembershipRankRepository membershipRankRepository;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Danh sách hạng")
    public ResponseEntity<ApiResponse<List<MembershipRankDTO>>> list() {
        List<MembershipRankDTO> data = membershipRankRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.<List<MembershipRankDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách hạng thành công")
                .data(data)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết hạng")
    public ResponseEntity<ApiResponse<MembershipRankDTO>> getById(@PathVariable Integer id) {
        MembershipRank r = membershipRankRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hạng với id: " + id));
        return ResponseEntity.ok(ApiResponse.<MembershipRankDTO>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(toDTO(r))
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo hạng")
    public ResponseEntity<ApiResponse<MembershipRankDTO>> create(@RequestBody MembershipRankDTO dto) {
        validate(dto);
        MembershipRank saved = membershipRankRepository.save(fromDTO(new MembershipRank(), dto));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<MembershipRankDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo hạng thành công")
                .data(toDTO(saved))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật hạng")
    public ResponseEntity<ApiResponse<MembershipRankDTO>> update(@PathVariable Integer id,
            @RequestBody MembershipRankDTO dto) {
        MembershipRank r = membershipRankRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hạng với id: " + id));
        validate(dto);

        boolean hasChanges = applyChanges(r, dto);

        if (!hasChanges) {
            return ResponseEntity.ok(ApiResponse.<MembershipRankDTO>builder()
                    .status(HttpStatus.OK.value())
                    .message("Không có thay đổi để cập nhật")
                    .data(toDTO(r))
                    .build());
        }

        MembershipRank saved = membershipRankRepository.save(r);
        return ResponseEntity.ok(ApiResponse.<MembershipRankDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật hạng thành công")
                .data(toDTO(saved))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa hạng")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        if (!membershipRankRepository.existsById(id)) {
            throw new RuntimeException("Không tìm thấy hạng với id: " + id);
        }
        if (userRepository.existsByRankId(id)) {
            throw new RuntimeException("Không thể xóa hạng vì đang có người dùng thuộc hạng này");
        }
        membershipRankRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa hạng thành công")
                .build());
    }

    private void validate(MembershipRankDTO dto) {
        if (dto == null) {
            throw new RuntimeException("Dữ liệu không hợp lệ");
        }
        if (dto.getRankName() == null || dto.getRankName().trim().isEmpty()) {
            throw new RuntimeException("Tên hạng không được để trống");
        }
        if (dto.getMinSpending() == null || dto.getMinSpending() < 0) {
            throw new RuntimeException("Chi tiêu tối thiểu không hợp lệ");
        }
        if (dto.getDiscountPercent() == null || dto.getDiscountPercent() < 0 || dto.getDiscountPercent() > 100) {
            throw new RuntimeException("Phần trăm giảm phải từ 0 đến 100");
        }
        if (dto.getBonusPoint() == null || dto.getBonusPoint() < 1) {
            throw new RuntimeException("Điểm thưởng phải >= 1");
        }
        if (dto.getStatus() == null || (dto.getStatus() != 0 && dto.getStatus() != 1)) {
            throw new RuntimeException("Trạng thái không hợp lệ (0: ngừng hoạt động, 1: hoạt động)");
        }
    }

    private MembershipRankDTO toDTO(MembershipRank r) {
        return MembershipRankDTO.builder()
                .id(r.getRankId())
                .rankName(r.getRankName())
                .minSpending(r.getMinSpending())
                .description(r.getDescription())
                .discountPercent(r.getDiscountPercent())
                .bonusPoint(r.getBonusPoint())
                .status(r.getStatus())
                .build();
    }

    private MembershipRank fromDTO(MembershipRank r, MembershipRankDTO dto) {
        r.setRankName(dto.getRankName() != null ? dto.getRankName().trim() : r.getRankName());
        r.setMinSpending(dto.getMinSpending());
        r.setDescription(dto.getDescription());
        r.setDiscountPercent(dto.getDiscountPercent());
        r.setBonusPoint(dto.getBonusPoint());
        r.setStatus(dto.getStatus());
        return r;
    }

    private boolean applyChanges(MembershipRank r, MembershipRankDTO dto) {
        boolean hasChanges = false;

        if (dto.getRankName() != null && !dto.getRankName().trim().isEmpty()) {
            String newName = dto.getRankName().trim();
            if (!newName.equals(r.getRankName())) {
                r.setRankName(newName);
                hasChanges = true;
            }
        }

        if (dto.getMinSpending() != null && !dto.getMinSpending().equals(r.getMinSpending())) {
            r.setMinSpending(dto.getMinSpending());
            hasChanges = true;
        }

        if (dto.getDescription() != null && !dto.getDescription().equals(r.getDescription())) {
            r.setDescription(dto.getDescription());
            hasChanges = true;
        }

        if (dto.getDiscountPercent() != null && !dto.getDiscountPercent().equals(r.getDiscountPercent())) {
            r.setDiscountPercent(dto.getDiscountPercent());
            hasChanges = true;
        }

        if (dto.getBonusPoint() != null && !dto.getBonusPoint().equals(r.getBonusPoint())) {
            r.setBonusPoint(dto.getBonusPoint());
            hasChanges = true;
        }

        if (dto.getStatus() != null && !dto.getStatus().equals(r.getStatus())) {
            r.setStatus(dto.getStatus());
            hasChanges = true;
        }

        return hasChanges;
    }
}
