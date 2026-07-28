package com.fpoly.duan.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaffDTO {
    private Integer staffId;

    @NotBlank(message = "Email nhân viên không được để trống")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Họ và tên không được để trống")
    private String fullname;

    private Integer status;
    private String phone;
    private LocalDate birthday;

    @NotBlank(message = "Vai trò không được để trống")
    private String role;

    private String avatar;

    private Integer cinemaId;

    private String cinemaName;

    /** Chỉ gửi khi tạo mới. Nếu để trống: hệ thống sinh mật khẩu ngẫu nhiên và gửi qua email. */
    @JsonProperty(access = Access.WRITE_ONLY)
    private String password;
}
