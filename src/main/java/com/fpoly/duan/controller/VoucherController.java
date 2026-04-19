package com.fpoly.duan.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
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
import com.fpoly.duan.repository.UserVoucherRepository;
import com.fpoly.duan.repository.VoucherRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/vouchers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "8b. Voucher", description = "CRUD mã giảm giá — Super Admin.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
@Slf4j
// [SUPER ADMIN ONLY] - This section belongs to Super Admin. Do not modify without authorization.
public class VoucherController {

    private final VoucherRepository voucherRepository;
    private final UserVoucherRepository userVoucherRepository;

    @GetMapping
    @Operation(summary = "Danh sách voucher")
    public ResponseEntity<ApiResponse<List<VoucherDTO>>> list() {
        try {
            List<VoucherDTO> data = voucherRepository.findAll().stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(ApiResponse.<List<VoucherDTO>>builder()
                    .status(HttpStatus.OK.value())
                    .message("Lấy danh sách voucher thành công")
                    .data(data)
                    .build());
        } catch (Exception e) {
            log.error("Lỗi khi lấy danh sách voucher: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.<List<VoucherDTO>>builder()
                            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                            .message("Lỗi Server: " + e.getMessage())
                            .build());
        }
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
        try {
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
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<VoucherDTO>builder()
                            .status(HttpStatus.BAD_REQUEST.value())
                            .message(e.getMessage())
                            .build());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Cập nhật voucher")
    public ResponseEntity<ApiResponse<VoucherDTO>> update(@PathVariable Integer id, @RequestBody VoucherDTO dto) {
        try {
            Voucher v = voucherRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy voucher với id: " + id));
            validate(dto, false);

            boolean hasChanges = false;
            String newCode = dto.getCode() != null ? dto.getCode().trim() : v.getCode();
            
            if (newCode != null && !newCode.equalsIgnoreCase(v.getCode())) hasChanges = true;
            if (dto.getValue() != null && !dto.getValue().equals(v.getValue())) hasChanges = true;
            if (dto.getMinOrderValue() != null && !dto.getMinOrderValue().equals(v.getMinOrderValue())) hasChanges = true;
            if (dto.getMaxDiscountAmount() != null && !dto.getMaxDiscountAmount().equals(v.getMaxDiscountAmount())) hasChanges = true;
            if (dto.getStartDate() != null && !dto.getStartDate().equals(v.getStartDate())) hasChanges = true;
            if (dto.getEndDate() != null && !dto.getEndDate().equals(v.getEndDate())) hasChanges = true;
            if (dto.getPointVoucher() != null && !dto.getPointVoucher().equals(v.getPointVoucher())) hasChanges = true;
            
            // So sánh trạng thái (0: Dừng phát hành, 1: Đang phát hành, 2: Chờ phát hành, 3: Đã kết thúc)
            // Cần so sánh với giá trị trong DB (v.getStatus())
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
                throw new RuntimeException("Mã voucher đã tồn tại");
            }
            Voucher saved = voucherRepository.save(fromDTO(v, dto));
            return ResponseEntity.ok(ApiResponse.<VoucherDTO>builder()
                    .status(HttpStatus.OK.value())
                    .message("Cập nhật voucher thành công")
                    .data(toDTO(saved))
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<VoucherDTO>builder()
                            .status(HttpStatus.BAD_REQUEST.value())
                            .message(e.getMessage())
                            .build());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa voucher")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable @NonNull Integer id) {
        try {
            if (!voucherRepository.existsById(id)) {
                throw new RuntimeException("Không tìm thấy voucher với id: " + id);
            }

            // Kiểm tra xem voucher đã được người dùng nào đổi hoặc sử dụng chưa
            if (userVoucherRepository.existsByVoucher_VouchersId(id)) {
                throw new RuntimeException("Không thể xóa voucher này vì đã có khách hàng sở hữu hoặc đã sử dụng. Hãy chuyển sang trạng thái Dừng phát hành.");
            }

            voucherRepository.deleteById(id);
            return ResponseEntity.ok(ApiResponse.<Void>builder()
                    .status(HttpStatus.OK.value())
                    .message("Xóa voucher thành công")
                    .build());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.<Void>builder()
                            .status(HttpStatus.BAD_REQUEST.value())
                            .message(e.getMessage())
                            .build());
        }
    }

    private void validate(VoucherDTO dto, boolean isCreate) {
        if (dto == null) {
            throw new RuntimeException("Dữ liệu không hợp lệ");
        }
        if (isCreate && (dto.getCode() == null || dto.getCode().trim().isEmpty())) {
            throw new RuntimeException("Mã voucher không được để trống");
        }
        if (dto.getValue() == null || dto.getValue() <= 0 || dto.getValue() > 100) {
            throw new RuntimeException("Giá trị giảm (%) phải từ 0 đến 100");
        }
        if (dto.getMinOrderValue() == null || dto.getMinOrderValue() < 0) {
            throw new RuntimeException("Đơn tối thiểu không hợp lệ");
        }
        if (dto.getMaxDiscountAmount() == null || dto.getMaxDiscountAmount() < 0) {
            throw new RuntimeException("Số tiền giảm tối đa không hợp lệ");
        }
        if (dto.getStartDate() == null || dto.getEndDate() == null) {
            throw new RuntimeException("Ngày bắt đầu / kết thúc không được để trống");
        }
        if (dto.getEndDate().isBefore(dto.getStartDate())) {
            throw new RuntimeException("Ngày kết thúc phải sau ngày bắt đầu");
        }

        LocalDate now = LocalDate.now();
        if (dto.getStatus() != null && dto.getStatus() == 1) { 
            if (now.isBefore(dto.getStartDate())) {
                throw new RuntimeException("Chưa đến thời gian phát hành. Vui lòng chọn trạng thái 'Chờ phát hành'.");
            }
            if (now.isAfter(dto.getEndDate())) {
                throw new RuntimeException("Ngày kết thúc đã qua. Voucher này đã kết thúc.");
            }
        }

        if (dto.getPointVoucher() == null || dto.getPointVoucher() < 0) {
            throw new RuntimeException("Điểm đổi voucher không hợp lệ");
        }
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
                .value(v.getValue() != null ? v.getValue() : 0.0)
                .minOrderValue(v.getMinOrderValue() != null ? v.getMinOrderValue() : 0.0)
                .maxDiscountAmount(v.getMaxDiscountAmount() != null ? v.getMaxDiscountAmount() : 0.0)
                .startDate(start)
                .endDate(end)
                .pointVoucher(v.getPointVoucher() != null ? v.getPointVoucher() : 0)
                .status(displayStatus)
                .build();
    }

    private Voucher fromDTO(Voucher v, VoucherDTO dto) {
        if (dto.getCode() != null) {
            v.setCode(dto.getCode().trim());
        }
        v.setValue(dto.getValue());
        v.setMinOrderValue(dto.getMinOrderValue());
        v.setMaxDiscountAmount(dto.getMaxDiscountAmount());
        v.setStartDate(dto.getStartDate());
        v.setEndDate(dto.getEndDate());
        v.setPointVoucher(dto.getPointVoucher());
        
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
}
