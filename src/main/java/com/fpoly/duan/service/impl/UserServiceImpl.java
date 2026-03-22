package com.fpoly.duan.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.duan.dto.UserDTO;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.repository.StaffRepository;
import com.fpoly.duan.repository.UserRepository;
import com.fpoly.duan.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getUserById(Integer id) {
        return userRepository.findById(id)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với mã: " + id));
    }

    @Override
    @Transactional(readOnly = true)
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
        if (staffRepository.existsByEmail(userDTO.getEmail().trim())) {
            throw new RuntimeException("Email đã được dùng cho tài khoản nhân viên");
        }
        if (staffRepository.existsByUsername(userDTO.getUsername().trim())) {
            throw new RuntimeException("Tên đăng nhập đã được dùng cho tài khoản nhân viên");
        }
        String phoneCreate = userDTO.getPhone() != null ? userDTO.getPhone().trim() : "";
        if (!phoneCreate.isEmpty()) {
            if (Boolean.TRUE.equals(userRepository.existsByPhone(phoneCreate))) {
                throw new RuntimeException("Số điện thoại đã tồn tại");
            }
            if (Boolean.TRUE.equals(staffRepository.existsByPhone(phoneCreate))) {
                throw new RuntimeException("Số điện thoại đã được dùng cho tài khoản nhân viên");
            }
        }

        User user = convertToEntity(userDTO);
        user.setPassword(passwordEncoder.encode(password));
        return convertToDTO(userRepository.save(user));
    }

    @Override
    public UserDTO updateUser(Integer id, UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với mã: " + id));

        if (userDTO.getEmail() != null && !userDTO.getEmail().isBlank()) {
            String newEmail = userDTO.getEmail().trim();
            if (!newEmail.equalsIgnoreCase(user.getEmail() == null ? "" : user.getEmail())) {
                userRepository.findByEmail(newEmail).ifPresent(other -> {
                    if (!other.getUserId().equals(id)) {
                        throw new RuntimeException("Email đã được sử dụng bởi tài khoản khác");
                    }
                });
                if (staffRepository.existsByEmail(newEmail)) {
                    throw new RuntimeException("Email đã được dùng cho tài khoản nhân viên");
                }
            }
            user.setEmail(newEmail);
        }

        if (userDTO.getFullname() != null) {
            user.setFullname(userDTO.getFullname().trim());
        }
        if (userDTO.getPhone() != null) {
            String newPhone = userDTO.getPhone().trim();
            String oldPhone = user.getPhone() == null ? "" : user.getPhone().trim();
            if (!newPhone.equals(oldPhone)) {
                if (Boolean.TRUE.equals(userRepository.existsByPhoneAndUserIdNot(newPhone, id))) {
                    throw new RuntimeException("Số điện thoại đã được sử dụng bởi tài khoản khác");
                }
                if (Boolean.TRUE.equals(staffRepository.existsByPhone(newPhone))) {
                    throw new RuntimeException("Số điện thoại đã được dùng cho tài khoản nhân viên");
                }
            }
            user.setPhone(newPhone);
        }
        if (userDTO.getStatus() != null) {
            user.setStatus(userDTO.getStatus());
        }
        user.setBirthday(userDTO.getBirthday());
        if (userDTO.getAvatar() != null) {
            user.setAvatar(userDTO.getAvatar().trim().isEmpty() ? null : userDTO.getAvatar().trim());
        }

        return convertToDTO(userRepository.save(user));
    }

    @Override
    public void changePassword(Integer userId, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            throw new RuntimeException("Mật khẩu mới tối thiểu 8 ký tự");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với mã: " + userId));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Mật khẩu hiện tại không đúng");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    private UserDTO convertToDTO(User user) {
        var rank = user.getRank();
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
                .rankName(rank != null ? rank.getRankName() : null)
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
        return user;
    }
}
