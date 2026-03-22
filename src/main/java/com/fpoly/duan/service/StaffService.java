package com.fpoly.duan.service;

import java.util.List;

import com.fpoly.duan.dto.StaffDTO;

public interface StaffService {
    List<StaffDTO> getAllStaff();

    /** {@code cinemaId == null}: toàn bộ; ngược lại: nhân viên thuộc rạp đó hoặc chưa gán rạp (null). */
    List<StaffDTO> listStaffByCinema(Integer cinemaId);

    StaffDTO getStaffById(Integer id);

    StaffDTO createStaff(StaffDTO staffDTO);

    StaffDTO updateStaff(Integer id, StaffDTO staffDTO);

    void changePassword(Integer staffId, String currentPassword, String newPassword);

    void deleteStaff(Integer id);
}

