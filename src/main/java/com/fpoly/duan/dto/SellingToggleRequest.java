package com.fpoly.duan.dto;

import lombok.Data;

@Data
public class SellingToggleRequest {
    /** true = mở bán tại rạp; false = chuyển về chưa bán (tắt) */
    private Boolean selling;
}
