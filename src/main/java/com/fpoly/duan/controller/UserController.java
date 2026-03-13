package com.fpoly.duan.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.UserDTO;
import com.fpoly.duan.dto.UserRequest;
import com.fpoly.duan.service.UserService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserDTO>>> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(ApiResponse.<List<UserDTO>>builder()
                .status(200)
                .message("Lấy danh sách người dùng thành công")
                .data(users)
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> getUserById(@PathVariable Integer id) {
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.<UserDTO>builder()
                .status(200)
                .message("Lấy thông tin người dùng thành công")
                .data(user)
                .build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserDTO>> createUser(@RequestBody UserRequest userRequest) {
        UserDTO userDTO = UserDTO.builder()
                .username(userRequest.getUsername())
                .fullname(userRequest.getFullname())
                .email(userRequest.getEmail())
                .phone(userRequest.getPhone())
                .birthday(userRequest.getBirthday())
                .avatar(userRequest.getAvatar())
                .role(userRequest.getRole())
                .build();
        UserDTO createdUser = userService.createUser(userDTO, userRequest.getPassword());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.<UserDTO>builder()
                .status(201)
                .message("Tạo người dùng thành công")
                .data(createdUser)
                .build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<UserDTO>> updateUser(@PathVariable Integer id, @RequestBody UserDTO userDTO) {
        UserDTO updatedUser = userService.updateUser(id, userDTO);
        return ResponseEntity.ok(ApiResponse.<UserDTO>builder()
                .status(200)
                .message("Cập nhật người dùng thành công")
                .data(updatedUser)
                .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Integer id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(200)
                .message("Xóa người dùng thành công")
                .build());
    }
}
