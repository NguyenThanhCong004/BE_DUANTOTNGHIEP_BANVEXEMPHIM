package com.fpoly.duan.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fpoly.duan.dto.UserDTO;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.repository.UserRepository;
import com.fpoly.duan.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public UserDTO getUserById(Integer id) {
        return userRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với mã: " + id));
    }

    @Override
    public UserDTO getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với tên đăng nhập: " + username));
    }

    @Override
    public UserDTO createUser(UserDTO userDTO, String password) {
        if (userRepository.existsByUsername(userDTO.getUsername())) {
            throw new RuntimeException("Tên đăng nhập đã tồn tại");
        }
        if (userRepository.existsByEmail(userDTO.getEmail())) {
            throw new RuntimeException("Email đã tồn tại");
        }

        User user = convertToEntity(userDTO);
        user.setPassword(passwordEncoder.encode(password));
        return convertToDTO(userRepository.save(user));
    }

    @Override
    public UserDTO updateUser(Integer id, UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với mã: " + id));

        user.setFullname(userDTO.getFullname());
        user.setEmail(userDTO.getEmail());
        user.setPhone(userDTO.getPhone());
        user.setStatus(userDTO.getStatus());
        user.setBirthday(userDTO.getBirthday());
        user.setAvatar(userDTO.getAvatar());
        user.setRole(userDTO.getRole());

        return convertToDTO(userRepository.save(user));
    }

    @Override
    public void deleteUser(Integer id) {
        userRepository.deleteById(id);
    }

    private UserDTO convertToDTO(User user) {
        return UserDTO.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .fullname(user.getFullname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .status(user.getStatus())
                .birthday(user.getBirthday())
                .avatar(user.getAvatar())
                .points(user.getPoints())
                .totalSpending(user.getTotalSpending())
                .role(user.getRole())
                .build();
    }

    private User convertToEntity(UserDTO userDTO) {
        User user = new User();
        user.setUserId(userDTO.getUserId());
        user.setUsername(userDTO.getUsername());
        user.setFullname(userDTO.getFullname());
        user.setEmail(userDTO.getEmail());
        user.setPhone(userDTO.getPhone());
        user.setStatus(userDTO.getStatus());
        user.setBirthday(userDTO.getBirthday());
        user.setAvatar(userDTO.getAvatar());
        user.setPoints(userDTO.getPoints() != null ? userDTO.getPoints() : 0);
        user.setTotalSpending(userDTO.getTotalSpending() != null ? userDTO.getTotalSpending() : 0.0);
        user.setRole(userDTO.getRole() != null ? userDTO.getRole() : 0);
        return user;
    }
}
