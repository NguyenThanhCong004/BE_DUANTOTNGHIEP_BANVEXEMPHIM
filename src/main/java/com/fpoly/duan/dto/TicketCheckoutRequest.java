package com.fpoly.duan.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tạo đơn vé + link PayOS")
public class TicketCheckoutRequest {

    @NotNull
    @Positive
    @Schema(description = "ID suất chiếu")
    private Integer showtimeId;

    @NotEmpty
    @Size(max = 20)
    @Schema(description = "Danh sách seat_id đã chọn")
    private List<Integer> seatIds;

    @NotNull
    @Schema(description = "URL FE khi PayOS redirect thành công", example = "http://localhost:5173/payment/success")
    private String returnUrl;

    @NotNull
    @Schema(description = "URL khi hủy thanh toán", example = "http://localhost:5173/payment/cancel")
    private String cancelUrl;

    @Size(max = 80)
    @Schema(description = "UUID phiên FE — trùng với seat-holds; checkout kiểm tra không bị người khác giữ ghế")
    private String clientHoldId;

    @Size(max = 30)
    @Valid
    @Schema(description = "Bắp nước kèm vé — sản phẩm phải đang bán tại rạp của suất chiếu")
    private List<SnackLineRequest> snacks;
}
