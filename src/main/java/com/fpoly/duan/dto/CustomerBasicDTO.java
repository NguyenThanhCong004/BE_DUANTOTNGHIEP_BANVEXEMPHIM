package com.fpoly.duan.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CustomerBasicDTO {
    private Integer userId;
    private String fullname;
    private String phone;
    private String email;
    private Boolean isNew;
}
