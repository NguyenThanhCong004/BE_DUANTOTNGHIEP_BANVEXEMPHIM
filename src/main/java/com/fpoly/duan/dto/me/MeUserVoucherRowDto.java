package com.fpoly.duan.dto.me;

import com.fpoly.duan.dto.VoucherDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeUserVoucherRowDto {
    private Integer userVoucherId;
    /** 1 = chưa dùng, 0 = đã dùng (theo quy ước FE) */
    private Integer status;
    private VoucherDTO voucher;
}
