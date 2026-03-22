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
import com.fpoly.duan.dto.VoucherDTO;
import com.fpoly.duan.entity.Voucher;
import com.fpoly.duan.repository.VoucherRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "8b. Voucher", description = "CRUD mã giảm giá — Super Admin.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class VoucherController {

    private final VoucherRepository voucherRepository;

    @GetMapping
    @Operation(summary = "Danh sách voucher")
    public ResponseEntity<ApiResponse<List<VoucherDTO>>> list() {
        List<VoucherDTO> data = voucherRepository.findAll().stream()
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
                .orElseThrow(() -> new RuntimeException("Không tìm thấy voucher với id: " + id));
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
            throw new RuntimeException("Mã voucher đã tồn tại");
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
                .orElseThrow(() -> new RuntimeException("Không tìm thấy voucher với id: " + id));
        validate(dto, false);
        String newCode = dto.getCode() != null ? dto.getCode().trim() : v.getCode();
        if (newCode != null && voucherRepository.findAll().stream()
                .anyMatch(x -> !x.getVouchersId().equals(id) && x.getCode() != null
                        && x.getCode().equalsIgnoreCase(newCode))) {
            throw new RuntimeException("Mã voucher đã tồn tại");
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
            throw new RuntimeException("Không tìm thấy voucher với id: " + id);
        }
        voucherRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Xóa voucher thành công")
                .build());
    }

    private void validate(VoucherDTO dto, boolean isCreate) {
        if (dto == null) {
            throw new RuntimeException("Dữ liệu không hợp lệ");
        }
        if (isCreate && (dto.getCode() == null || dto.getCode().trim().isEmpty())) {
            throw new RuntimeException("Mã voucher không được để trống");
        }
        if (dto.getDiscountType() == null || dto.getDiscountType().trim().isEmpty()) {
            throw new RuntimeException("Loại giảm giá không được để trống");
        }
        if (dto.getValue() == null || dto.getValue() <= 0) {
            throw new RuntimeException("Giá trị giảm không hợp lệ");
        }
        if ("PERCENTAGE".equalsIgnoreCase(dto.getDiscountType()) && dto.getValue() > 100) {
            throw new RuntimeException("Phần trăm giảm không được vượt quá 100");
        }
        if (dto.getMinOrderValue() == null || dto.getMinOrderValue() < 0) {
            throw new RuntimeException("Đơn tối thiểu không hợp lệ");
        }
        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw new RuntimeException("Ngày bắt đầu / kết thúc không được để trống");
        }
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new RuntimeException("Ngày kết thúc phải sau ngày bắt đầu");
        }
        if (dto.getPointVoucher() == null || dto.getPointVoucher() < 0) {
            throw new RuntimeException("Điểm đổi voucher không hợp lệ");
        }
    }

    private VoucherDTO toDTO(Voucher v) {
        return VoucherDTO.builder()
                .id(v.getVouchersId())
                .code(v.getCode())
                .discountType(v.getDiscountType())
                .value(v.getValue())
                .minOrderValue(v.getMinOrderValue())
                .startDate(v.getStartDate())
                .endDate(v.getEndDate())
                .pointVoucher(v.getPointVoucher())
                .status(v.getStatus() != null ? v.getStatus() : 1)
                .build();
    }

    private Voucher fromDTO(Voucher v, VoucherDTO dto) {
        if (dto.getCode() != null) {
            v.setCode(dto.getCode().trim());
        }
        v.setDiscountType(dto.getDiscountType() != null ? dto.getDiscountType().trim() : "PERCENTAGE");
        v.setValue(dto.getValue());
        v.setMinOrderValue(dto.getMinOrderValue());
        v.setStartDate(dto.getStartDate());
        v.setEndDate(dto.getEndDate());
        v.setPointVoucher(dto.getPointVoucher());
        v.setStatus(dto.getStatus() != null ? dto.getStatus() : 1);
        return v;
    }
}
