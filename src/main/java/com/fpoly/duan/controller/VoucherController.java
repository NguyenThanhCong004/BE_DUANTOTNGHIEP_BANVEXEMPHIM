package com.fpoly.duan.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.VoucherDTO;
import com.fpoly.duan.entity.Voucher;
import com.fpoly.duan.repository.UserVoucherRepository;
import com.fpoly.duan.repository.VoucherRepository;
import com.fpoly.duan.util.SearchUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "8b. Voucher", description = "CRUD mã giảm giá — Admin, Super Admin.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
@SuppressWarnings("null")
public class VoucherController {

    private static final BigDecimal MAX_POINT_VOUCHER = BigDecimal.valueOf(10_000_000L);

    private final VoucherRepository voucherRepository;
    private final UserVoucherRepository userVoucherRepository;

    @GetMapping
    @Operation(summary = "Danh sách voucher")
    public ResponseEntity<ApiResponse<List<VoucherDTO>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q) {
        String term = SearchUtils.pick(search, keyword, q);
        List<VoucherDTO> data = voucherRepository.findAll().stream()
                .filter(v -> SearchUtils.matches(term,
                        v.getVouchersId(), v.getCode(), v.getValue(), v.getMinOrderValue(),
                        v.getMaxDiscountAmount(), v.getPointVoucher(), v.getStatus()))
                .map(this::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.<List<VoucherDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("Lấy danh sách voucher thành công")
                .data(data)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết voucher")
    public ResponseEntity<ApiResponse<VoucherDTO>> getById(@PathVariable Integer id) {
        Voucher v = voucherRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy voucher với id: " + id));
        return ResponseEntity.ok(ApiResponse.<VoucherDTO>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(toDTO(v))
                .build());
    }

    @PostMapping
    @Operation(summary = "Tạo voucher")
    public ResponseEntity<ApiResponse<VoucherDTO>> create(@RequestBody VoucherDTO dto) {
        validate(dto, true);
        if (voucherRepository.findAll().stream().anyMatch(v -> v.getCode() != null
                && v.getCode().equalsIgnoreCase(dto.getCode().trim()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã voucher đã tồn tại");
        }
        Voucher saved = voucherRepository.save(fromDTO(new Voucher(), dto));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<VoucherDTO>builder()
                .status(HttpStatus.CREATED.value())
                .message("Tạo voucher thành công")
                .data(toDTO(saved))
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật voucher")
    public ResponseEntity<ApiResponse<VoucherDTO>> update(@PathVariable Integer id, @RequestBody VoucherDTO dto) {
        Voucher v = voucherRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy voucher với id: " + id));
        validate(dto, false);

        boolean hasChanges = false;
        String newCode = dto.getCode() != null ? dto.getCode().trim() : v.getCode();

        if (newCode != null && !newCode.equalsIgnoreCase(v.getCode())) hasChanges = true;
        if (dto.getValue() != null && !dto.getValue().equals(v.getValue())) hasChanges = true;
        if (dto.getMinOrderValue() != null && !dto.getMinOrderValue().equals(v.getMinOrderValue())) hasChanges = true;
        if (dto.getMaxDiscountAmount() != null && !dto.getMaxDiscountAmount().equals(v.getMaxDiscountAmount())) hasChanges = true;
        if (dto.getStartDate() != null && !dto.getStartDate().equals(v.getStartDate())) hasChanges = true;
        if (dto.getEndDate() != null && !dto.getEndDate().equals(v.getEndDate())) hasChanges = true;
        if (dto.getPointVoucher() != null && dto.getPointVoucher()
                .compareTo(BigDecimal.valueOf(v.getPointVoucher() != null ? v.getPointVoucher() : 0)) != 0) {
            hasChanges = true;
        }
        if (dto.getStatus() != null && !dto.getStatus().equals(v.getStatus())) hasChanges = true;

        if (!hasChanges) {
            return ResponseEntity.ok(ApiResponse.<VoucherDTO>builder()
                    .status(HttpStatus.OK.value())
                    .message("Không có thay đổi để cập nhật")
                    .data(toDTO(v))
                    .build());
        }

        if (newCode != null && voucherRepository.findAll().stream()
                .anyMatch(x -> !x.getVouchersId().equals(id) && x.getCode() != null
                        && x.getCode().equalsIgnoreCase(newCode))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã voucher đã tồn tại");
        }
        Voucher saved = voucherRepository.save(fromDTO(v, dto));
        return ResponseEntity.ok(ApiResponse.<VoucherDTO>builder()
                .status(HttpStatus.OK.value())
                .message("Cập nhật voucher thành công")
                .data(toDTO(saved))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa voucher")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer id) {
        if (!voucherRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy voucher với id: " + id);
        }
        if (userVoucherRepository.existsByVoucher_VouchersId(id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Không thể xóa voucher này vì đã có khách hàng sở hữu hoặc đã sử dụng. Hãy chuyển sang trạng thái Dừng phát hành.");
        }
        voucherRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa voucher thành công")
                .build());
    }

    private void validate(VoucherDTO dto, boolean isCreate) {
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ");
        }
        if (isCreate && (dto.getCode() == null || dto.getCode().trim().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã voucher không được để trống");
        }
        if (dto.getValue() == null || dto.getValue() <= 0 || dto.getValue() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá trị giảm (%) phải từ 0 đến 100");
        }
        if (dto.getMinOrderValue() == null || dto.getMinOrderValue() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn tối thiểu không hợp lệ");
        }
        if (dto.getMaxDiscountAmount() == null || dto.getMaxDiscountAmount() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số tiền giảm tối đa không hợp lệ");
        }
        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày bắt đầu / kết thúc không được để trống");
        }
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu");
        }

        LocalDate now = LocalDate.now();
        if (dto.getStatus() != null && dto.getStatus() == 1) {
            if (now.isBefore(dto.getStartDate())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Chưa đến thời gian phát hành. Vui lòng chọn trạng thái 'Chờ phát hành'.");
            }
            if (now.isAfter(dto.getEndDate())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ngày kết thúc đã qua. Voucher này đã kết thúc.");
            }
        }

        toPointVoucherInt(dto.getPointVoucher());
    }

    private VoucherDTO toDTO(Voucher v) {
        Integer rawStatus = v.getStatus() != null ? v.getStatus() : 0;
        Integer displayStatus = rawStatus;
        LocalDate now = LocalDate.now();

        LocalDate start = v.getStartDate();
        LocalDate end = v.getEndDate();

        if (rawStatus != 0 && start != null && end != null) {
            if (now.isBefore(start)) {
                displayStatus = 2; // Chờ phát hành
            } else if (now.isAfter(end)) {
                displayStatus = 3; // Đã kết thúc
            } else {
                displayStatus = 1; // Đang phát hành
            }
        }

        return VoucherDTO.builder()
                .id(v.getVouchersId())
                .code(v.getCode())
                .discountType(normalizeDiscountType(v.getDiscountType()))
                .value(v.getValue() != null ? v.getValue() : 0.0)
                .minOrderValue(v.getMinOrderValue() != null ? v.getMinOrderValue() : 0.0)
                .maxDiscountAmount(v.getMaxDiscountAmount() != null ? v.getMaxDiscountAmount() : 0.0)
                .startDate(start)
                .endDate(end)
                .pointVoucher(BigDecimal.valueOf(v.getPointVoucher() != null ? v.getPointVoucher() : 0))
                .status(displayStatus)
                .build();
    }

    private Voucher fromDTO(Voucher v, VoucherDTO dto) {
        if (dto.getCode() != null) {
            v.setCode(dto.getCode().trim());
        }
        v.setDiscountType(normalizeDiscountType(dto.getDiscountType()));
        v.setValue(dto.getValue());
        v.setMinOrderValue(dto.getMinOrderValue());
        v.setMaxDiscountAmount(dto.getMaxDiscountAmount());
        v.setStartDate(dto.getStartDate());
        v.setEndDate(dto.getEndDate());
        v.setPointVoucher(toPointVoucherInt(dto.getPointVoucher()));

        LocalDate now = LocalDate.now();
        Integer inputStatus = dto.getStatus() != null ? dto.getStatus() : 1;

        if (inputStatus == 0) {
            v.setStatus(0);
        } else {
            if (dto.getStartDate() != null && now.isBefore(dto.getStartDate())) {
                v.setStatus(2);
            } else if (dto.getEndDate() != null && now.isAfter(dto.getEndDate())) {
                v.setStatus(3);
            } else {
                v.setStatus(1);
            }
        }
        return v;
    }

    private String normalizeDiscountType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase();
        if (normalized.equals("FIXED") || normalized.equals("AMOUNT") || normalized.equals("MONEY")) {
            return "FIXED";
        }
        return "PERCENT";
    }

    private int toPointVoucherInt(BigDecimal value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Điểm đổi voucher không được để trống");
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Điểm đổi voucher không được âm");
        }
        if (normalized.compareTo(MAX_POINT_VOUCHER) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Điểm đổi voucher chỉ được từ 0 đến " + MAX_POINT_VOUCHER.toPlainString());
        }
        try {
            return normalized.intValueExact();
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Điểm đổi voucher phải là số nguyên từ 0 đến " + MAX_POINT_VOUCHER.toPlainString());
        }
    }
}
