package com.fpoly.duan.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReferenceCatalogDTO {
    private Integer id;
    @NotBlank(message = "Tên không được để trống")
    private String name;
    private Integer status;
}
