package com.fpoly.duan.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MembershipRankDTO {
    private Integer id;

    @NotBlank(message = "Tên hạng thành viên không được để trống")
    @Size(max = 100, message = "Tên hạng không được vượt quá 100 ký tự")
    private String rankName;

    @NotNull(message = "Mức chi tiêu tối thiểu không được để trống")
    @DecimalMin(value = "0.0", message = "Mức chi tiêu tối thiểu không được âm")
    private Double minSpending;

    private String description;

    @DecimalMin(value = "0.0", message = "Phần trăm giảm giá không được âm")
    @Max(value = 100, message = "Phần trăm giảm giá không được vượt quá 100")
    private Double discountPercent;

    @Min(value = 0, message = "Điểm bonus không được âm")
    private Integer bonusPoint;

    private Integer status;
    private Boolean isDefault;
}
