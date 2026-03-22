package com.fpoly.duan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "AuthResponse", description = "JWT + một trong hai: user (khách) hoặc staff (nhân sự)")
public class AuthResponse {
    @Schema(description = "Access token JWT", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    @Builder.Default
    @Schema(description = "Luôn là Bearer", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "Refresh token")
    private String refreshToken;

    @Schema(description = "Có khi đăng nhập tài khoản khách (user)")
    private UserDTO user;

    @Schema(description = "Có khi đăng nhập staff/admin")
    private StaffDTO staff;
}
