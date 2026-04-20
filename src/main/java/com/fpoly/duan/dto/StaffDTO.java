package com.fpoly.duan.dto;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

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
    private String email;
    private String username;
    private String fullname;
    private Integer status;
    private String phone;
    private LocalDate birthday;
    private String role;
    private String avatar;
    private Integer cinemaId;
    private String cinemaName;

    /** Chỉ gửi khi tạo mới. Nếu để trống: hệ thống sinh mật khẩu ngẫu nhiên và gửi qua email. */
    @JsonProperty(access = Access.WRITE_ONLY)
    private String password;
}
