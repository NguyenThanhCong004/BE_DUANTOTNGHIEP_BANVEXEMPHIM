package com.fpoly.duan.service;

import java.util.List;

import com.fpoly.duan.dto.UserDTO;

public interface UserService {
    List<UserDTO> getAllUsers();
    UserDTO getUserById(Integer id);
    UserDTO getUserByUsername(String username);
    UserDTO getUserByUsernameOrEmail(String usernameOrEmail);
    UserDTO createUser(UserDTO userDTO, String password);
    UserDTO updateUser(Integer id, UserDTO userDTO);

    /** Đổi mật khẩu (xác minh mật khẩu hiện tại). */
    void changePassword(Integer userId, String currentPassword, String newPassword);

    /** Đặt lại mật khẩu sau khi xác thực token quên mật khẩu (không cần mật khẩu cũ). */
    void resetPasswordByUserId(Integer userId, String newPassword);
}
