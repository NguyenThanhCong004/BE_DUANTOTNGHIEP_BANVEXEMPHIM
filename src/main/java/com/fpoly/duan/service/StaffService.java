package com.fpoly.duan.service;

import java.util.List;

import com.fpoly.duan.dto.StaffDTO;

public interface StaffService {
    List<StaffDTO> getAllStaff();

    /** New method for Super Admin: Get all staff except Super Admin without cinema filtering */
    List<StaffDTO> getAllStaffForSuperAdmin();

    /** {@code cinemaId == null}: toàn bộ; ngược lại: nhân viên thuộc rạp đó hoặc chưa gán rạp (null). */
    List<StaffDTO> listStaffByCinema(Integer cinemaId);

    StaffDTO getStaffById(Integer id);

    StaffDTO createStaff(StaffDTO staffDTO);

    StaffDTO updateStaff(Integer id, StaffDTO staffDTO);

    void changePassword(Integer staffId, String currentPassword, String newPassword);

    /** Đặt lại mật khẩu không cần mật khẩu hiện tại (dùng cho luồng quên mật khẩu qua OTP). */
    void resetPasswordByStaffId(Integer staffId, String newPassword);

    void deleteStaff(Integer id);
}

