package com.fpoly.duan.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.MembershipRankDTO;
import com.fpoly.duan.entity.MembershipRank;
import com.fpoly.duan.repository.MembershipRankRepository;
import com.fpoly.duan.service.UserService;
import com.fpoly.duan.util.SearchUtils;

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
@SuppressWarnings("null")
// [SUPER ADMIN ONLY] - This section belongs to Super Admin. Do not modify without authorization.
public class MembershipRankController {

    private final MembershipRankRepository membershipRankRepository;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "Danh sách hạng")
    public ResponseEntity<ApiResponse<List<MembershipRankDTO>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q) {
        String term = SearchUtils.pick(search, keyword, q);
        List<MembershipRankDTO> data = membershipRankRepository.findAllByOrderByMinSpendingAsc().stream()
                .filter(r -> SearchUtils.matches(term,
                        r.getRankId(), r.getRankName(), r.getDescription(), r.getMinSpending(),
                        r.getDiscountPercent(), r.getBonusPoint(), r.getStatus()))
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hạng với id: " + id));
        return ResponseEntity.ok(ApiResponse.<MembershipRankDTO>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(toDTO(r))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật hạng")
    public ResponseEntity<ApiResponse<MembershipRankDTO>> update(@PathVariable Integer id,
            @RequestBody MembershipRankDTO dto) {
        MembershipRank r = membershipRankRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hạng với id: " + id));

        if (r.getMinSpending() != null && r.getMinSpending() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không được phép chỉnh sửa hạng mặc định (0đ)");
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
        userService.recalculateAllUserRanks();

        return ResponseEntity.ok(ApiResponse.<MembershipRankDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật hạng thành công")
                .data(toDTO(saved))
                .build());
    }

    private void validate(MembershipRankDTO dto, Integer currentId) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ");
        }
        if (dto.getRankName() == null || dto.getRankName().trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên hạng không được để trống");
        }
        if (dto.getMinSpending() == null || dto.getMinSpending() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chi tiêu tối thiểu không hợp lệ (Phải >= 0)");
        }

        List<MembershipRank> allRanks = membershipRankRepository.findAllByOrderByMinSpendingAsc();

        boolean isDuplicate = allRanks.stream()
                .anyMatch(r -> !r.getRankId().equals(currentId) && r.getMinSpending().equals(dto.getMinSpending()));
        if (isDuplicate) {
            String valStr = dto.getMinSpending() == 0 ? "Mặc định (0đ)" : formatVNCurrency(dto.getMinSpending());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mức chi tiêu tối thiểu " + valStr + " đã tồn tại ở hạng khác");
        }

        boolean nameDuplicate = allRanks.stream()
                .anyMatch(r -> !r.getRankId().equals(currentId) && r.getRankName().equalsIgnoreCase(dto.getRankName().trim()));
        if (nameDuplicate) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tên hạng '" + dto.getRankName().trim() + "' đã tồn tại");
        }

        if (currentId != null) {
            int index = -1;
            for (int i = 0; i < allRanks.size(); i++) {
                if (allRanks.get(i).getRankId().equals(currentId)) {
                    index = i;
                    break;
                }
            }
            if (index != -1) {
                if (index > 0) {
                    MembershipRank lowerRank = allRanks.get(index - 1);
                    if (dto.getMinSpending() <= lowerRank.getMinSpending()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Chi tiêu tối thiểu phải cao hơn hạng '" + lowerRank.getRankName()
                                + "' (" + formatVNCurrency(lowerRank.getMinSpending()) + ")");
                    }
                }
                if (index < allRanks.size() - 1) {
                    MembershipRank higherRank = allRanks.get(index + 1);
                    if (dto.getMinSpending() >= higherRank.getMinSpending()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Chi tiêu tối thiểu phải thấp hơn hạng '" + higherRank.getRankName()
                                + "' (" + formatVNCurrency(higherRank.getMinSpending()) + ")");
                    }
                }
            }
        }

        if (dto.getDiscountPercent() == null || dto.getDiscountPercent() < 0 || dto.getDiscountPercent() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phần trăm giảm phải từ 0 đến 100");
        }
        if (dto.getBonusPoint() == null || dto.getBonusPoint() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Điểm thưởng phải >= 1");
        }
        if (dto.getStatus() == null || (dto.getStatus() != 0 && dto.getStatus() != 1)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ (0: ngừng hoạt động, 1: hoạt động)");
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
