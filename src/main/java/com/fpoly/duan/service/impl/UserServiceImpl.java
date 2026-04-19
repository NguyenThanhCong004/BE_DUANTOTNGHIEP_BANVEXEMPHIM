package com.fpoly.duan.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.duan.dto.UserDTO;
import com.fpoly.duan.entity.MembershipRank;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.repository.MembershipRankRepository;
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
    private final MembershipRankRepository membershipRankRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public List<UserDTO> getAllUsers() {
        List<User> users = userRepository.findAll();
        // Mỗi khi tải trang danh sách, tự động quét và cập nhật hạng cho tất cả User
        users.forEach(this::syncRank);
        return users.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private void syncRank(User user) {
        double totalSpending = user.getTotalSpending() != null ? user.getTotalSpending() : 0.0;
        MembershipRank bestRank = membershipRankRepository.findAll().stream()
                .filter(r -> totalSpending >= (r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                .sorted((r1, r2) -> {
                    double v1 = r1.getMinSpending() != null ? r1.getMinSpending() : 0.0;
                    double v2 = r2.getMinSpending() != null ? r2.getMinSpending() : 0.0;
                    return Double.compare(v2, v1);
                })
                .findFirst()
                .orElse(null);

        if (bestRank != null && (user.getRank() == null || !bestRank.getRankId().equals(user.getRank().getRankId()))) {
            user.setRank(bestRank);
            userRepository.save(user);
        }
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
        String key = username != null ? username.trim() : "";
        return userRepository.findFirstByUsernameOrderByUserIdAsc(key)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với tên đăng nhập: " + username));
    }

    @Override
    public UserDTO createUser(UserDTO userDTO, String password) {
        User user = convertToEntity(userDTO);
        user.setPassword(passwordEncoder.encode(password));
        return convertToDTO(userRepository.save(user));
    }

    @Override
    public UserDTO updateUser(Integer id, UserDTO userDTO) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với mã: " + id));

        // Cập nhật mọi trường thông tin được gửi lên (Khôi phục lại như lúc đầu)
        if (userDTO.getEmail() != null && !userDTO.getEmail().isBlank()) {
            user.setEmail(userDTO.getEmail().trim());
        }
        if (userDTO.getFullname() != null) {
            user.setFullname(userDTO.getFullname().trim());
        }
        if (userDTO.getPhone() != null) {
            user.setPhone(userDTO.getPhone().trim());
        }
        if (userDTO.getStatus() != null) {
            user.setStatus(userDTO.getStatus());
        }
        if (userDTO.getBirthday() != null) {
            user.setBirthday(userDTO.getBirthday());
        }
        if (userDTO.getAvatar() != null) {
            user.setAvatar(userDTO.getAvatar().trim());
        }
        if (userDTO.getPoints() != null) {
            user.setPoints(userDTO.getPoints());
        }

        // Tự động đồng bộ hạng sau khi cập nhật
        syncRank(user);

        return convertToDTO(userRepository.save(user));
    }

    @Override
    public void changePassword(Integer userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("Mật khẩu hiện tại không đúng");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    @Override
    public void resetPasswordByUserId(Integer userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));
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
                .rankId(rank != null ? rank.getRankId() : null)
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
