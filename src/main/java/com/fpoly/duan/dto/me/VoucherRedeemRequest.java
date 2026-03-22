package com.fpoly.duan.dto.me;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@Schema(description = "Đổi voucher bằng điểm")
public record VoucherRedeemRequest(
        @NotNull @Positive Integer voucherId) {
}
