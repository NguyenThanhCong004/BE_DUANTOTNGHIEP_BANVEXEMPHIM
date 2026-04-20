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
import com.fpoly.duan.service.UserService;

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
    private final UserService userService;

    @GetMapping
    @Operation(summary = "Danh sách hạng")
    public ResponseEntity<ApiResponse<List<MembershipRankDTO>>> list() {
        List<MembershipRankDTO> data = membershipRankRepository.findAllByOrderByMinSpendingAsc().stream()
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
        validate(dto, null);
        MembershipRank saved = membershipRankRepository.save(fromDTO(new MembershipRank(), dto));
        
        // Cập nhật lại hạng cho toàn bộ user
        userService.recalculateAllUserRanks();
        
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
        
        // Khóa không cho sửa hạng mặc định (0đ)
        if (r.getMinSpending() != null && r.getMinSpending() == 0) {
            throw new RuntimeException("Không được phép chỉnh sửa hạng mặc định (0đ)");
        }

        validate(dto, id);

        boolean hasChanges = applyChanges(r, dto);

        if (!hasChanges) {
            return ResponseEntity.ok(ApiResponse.<MembershipRankDTO>builder()
                    .status(HttpStatus.OK.value())
                    .message("Không có thay đổi để cập nhật")
                    .data(toDTO(r))
                    .build());
        }

        MembershipRank saved = membershipRankRepository.save(r);
        
        // Cập nhật lại hạng cho toàn bộ user (bao gồm cả trường hợp tắt trạng thái hoạt động)
        userService.recalculateAllUserRanks();

        return ResponseEntity.ok(ApiResponse.<MembershipRankDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật hạng thành công")
                .data(toDTO(saved))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa hạng")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        MembershipRank r = membershipRankRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hạng với id: " + id));

        // Khóa không cho xóa hạng mặc định (0đ)
        if (r.getMinSpending() != null && r.getMinSpending() == 0) {
            throw new RuntimeException("Không được phép xóa hạng mặc định (0đ)");
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

    private void validate(MembershipRankDTO dto, Integer currentId) {
        if (dto == null) {
            throw new RuntimeException("Dữ liệu không hợp lệ");
        }
        if (dto.getRankName() == null || dto.getRankName().trim().isEmpty()) {
            throw new RuntimeException("Tên hạng không được để trống");
        }
        if (dto.getMinSpending() == null || dto.getMinSpending() < 0) {
            throw new RuntimeException("Chi tiêu tối thiểu không hợp lệ (Phải >= 0)");
        }
        
        // Logic kiểm tra chi tiêu tối thiểu giữa các hạng
        List<MembershipRank> allRanks = membershipRankRepository.findAllByOrderByMinSpendingAsc();
        
        // 1. Kiểm tra trùng lặp minSpending (trừ hạng hiện tại nếu đang sửa)
        boolean isDuplicate = allRanks.stream()
            .anyMatch(r -> !r.getRankId().equals(currentId) && r.getMinSpending().equals(dto.getMinSpending()));
        if (isDuplicate) {
            String valStr = dto.getMinSpending() == 0 ? "Mặc định (0đ)" : formatVNCurrency(dto.getMinSpending());
            throw new RuntimeException("Mức chi tiêu tối thiểu " + valStr + " đã tồn tại ở hạng khác");
        }

        // 2. Kiểm tra tên hạng trùng lặp
        boolean nameDuplicate = allRanks.stream()
            .anyMatch(r -> !r.getRankId().equals(currentId) && r.getRankName().equalsIgnoreCase(dto.getRankName().trim()));
        if (nameDuplicate) {
            throw new RuntimeException("Tên hạng '" + dto.getRankName().trim() + "' đã tồn tại");
        }

        // 3. Kiểm tra ràng buộc thứ tự (chỉ áp dụng khi sửa để tránh phá vỡ cấu trúc hiện tại)
        if (currentId != null) {
            int index = -1;
            for (int i = 0; i < allRanks.size(); i++) {
                if (allRanks.get(i).getRankId().equals(currentId)) {
                    index = i;
                    break;
                }
            }
            
            if (index != -1) {
                // Kiểm tra với hạng thấp hơn liền kề
                if (index > 0) {
                    MembershipRank lowerRank = allRanks.get(index - 1);
                    if (dto.getMinSpending() <= lowerRank.getMinSpending()) {
                        throw new RuntimeException("Chi tiêu tối thiểu phải cao hơn hạng '" + lowerRank.getRankName() + "' (" + formatVNCurrency(lowerRank.getMinSpending()) + ")");
                    }
                }
                
                // Kiểm tra với hạng cao hơn liền kề
                if (index < allRanks.size() - 1) {
                    MembershipRank higherRank = allRanks.get(index + 1);
                    if (dto.getMinSpending() >= higherRank.getMinSpending()) {
                        throw new RuntimeException("Chi tiêu tối thiểu phải thấp hơn hạng '" + higherRank.getRankName() + "' (" + formatVNCurrency(higherRank.getMinSpending()) + ")");
                    }
                }
            }
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

    private String formatVNCurrency(Double amount) {
        if (amount == null || amount == 0) return "0đ";
        java.text.DecimalFormat formatter = new java.text.DecimalFormat("#,###");
        return formatter.format(amount).replace(',', '.') + "đ";
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
                .isDefault(r.getMinSpending() != null && r.getMinSpending() == 0)
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
