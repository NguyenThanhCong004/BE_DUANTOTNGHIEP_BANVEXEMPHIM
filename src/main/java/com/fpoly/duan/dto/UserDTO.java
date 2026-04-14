package com.fpoly.duan.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Integer userId;
    private String username;
    private String fullname;
    private String email;
    private String phone;
    private Integer status;
    private LocalDate birthday;
    private String avatar;
    private Integer points;
    private Double totalSpending;
    /** ID hạng hội viên để cập nhật */
    private Integer rankId;
    /** Tên hạng hội viên (đọc từ rank), chỉ hiển thị */
    private String rankName;
}
