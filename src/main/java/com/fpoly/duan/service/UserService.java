package com.fpoly.duan.service;

import java.util.List;

import com.fpoly.duan.dto.UserDTO;

public interface UserService {
    List<UserDTO> getAllUsers();
    UserDTO getUserById(Integer id);
    UserDTO getUserByUsername(String username);
    UserDTO createUser(UserDTO userDTO, String password);
    UserDTO updateUser(Integer id, UserDTO userDTO);
    void deleteUser(Integer id);
}
