package com.fpoly.duan.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPasswordChangeRequest {
    private String currentPassword;
    private String newPassword;
}
