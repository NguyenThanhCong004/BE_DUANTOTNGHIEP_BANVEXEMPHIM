package com.fpoly.duan.service.impl;

import java.util.List;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.duan.dto.UserDTO;
import com.fpoly.duan.entity.MembershipRank;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.repository.MembershipRankRepository;
import com.fpoly.duan.repository.OrderOnlineRepository;
import com.fpoly.duan.repository.UserRepository;
import com.fpoly.duan.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final MembershipRankRepository membershipRankRepository;
    private final OrderOnlineRepository orderOnlineRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
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
        String key = username != null ? username.trim() : "";
        return userRepository.findFirstByUsernameOrderByUserIdAsc(key)
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với tên đăng nhập: " + username));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDTO getUserByUsernameOrEmail(String usernameOrEmail) {
        String key = usernameOrEmail != null ? usernameOrEmail.trim() : "";
        return userRepository.findByUsernameIgnoreCase(key)
                .or(() -> userRepository.findByEmailIgnoreCase(key))
                .map(this::convertToDTO)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng với tên đăng nhập/email: " + key));
    }

    @Override
    public UserDTO createUser(UserDTO userDTO, String password) {
        User user = convertToEntity(userDTO);
        user.setRankId(resolveRankIdForWrite(userDTO.getRankId()));
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
        if (userDTO.getRankId() != null) {
            user.setRankId(resolveRankIdForWrite(userDTO.getRankId()));
        }

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

    @Override
    public void recalculateAllUserRanks() {
        List<User> allUsers = userRepository.findAll();
        List<MembershipRank> activeRanks = membershipRankRepository.findAll().stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                .sorted(Comparator.comparing(r -> r.getMinSpending() != null ? r.getMinSpending() : 0.0, Comparator.reverseOrder()))
                .collect(Collectors.toList());

        if (activeRanks.isEmpty()) {
            return;
        }

        int currentYear = LocalDate.now().getYear();
        for (User user : allUsers) {
            double completedRevenue = orderOnlineRepository
                    .sumCompletedRevenueByUserAndYear(user.getUserId(), currentYear);
            
            // Tìm hạng cao nhất mà người dùng đủ điều kiện
            MembershipRank matched = activeRanks.stream()
                    .filter(r -> completedRevenue >= (r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                    .findFirst()
                    .orElse(activeRanks.get(activeRanks.size() - 1)); // Nếu không đạt hạng nào, lấy hạng thấp nhất trong số các hạng đang hoạt động

            user.setTotalSpending(completedRevenue);
            user.setRankId(matched.getRankId());
        }
        userRepository.saveAll(allUsers);
    }

    private UserDTO convertToDTO(User user) {
        int currentYear = LocalDate.now().getYear();
        double currentYearSpending = orderOnlineRepository
                .sumCompletedRevenueByUserAndYear(user.getUserId(), currentYear);
        MembershipRank assignedRank = resolveRankBySpending(currentYearSpending);
        if (assignedRank == null) {
            assignedRank = resolveRankEntityForRead(user.getRankId());
        }

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
                .totalSpending(currentYearSpending)
                .rankId(assignedRank != null ? assignedRank.getRankId() : null)
                .rankName(assignedRank != null ? assignedRank.getRankName() : "Hạng Đồng")
                .build();
    }

    private MembershipRank resolveRankBySpending(double spending) {
        List<MembershipRank> activeRanks = membershipRankRepository.findAll().stream()
                .filter(r -> r.getStatus() == null || r.getStatus() == 1)
                .toList();
        if (activeRanks.isEmpty()) return null;
        MembershipRank matched = activeRanks.stream()
                .filter(r -> spending >= (r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                .max(Comparator.comparing(r -> r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                .orElse(null);
        if (matched != null) return matched;
        return activeRanks.stream()
                .min(Comparator.comparing(r -> r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                .orElse(null);
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
        user.setRankId(resolveRankIdForWrite(userDTO.getRankId()));
        user.setTotalSpending(userDTO.getTotalSpending() != null ? userDTO.getTotalSpending() : 0.0);
        return user;
    }

    private Integer resolveRankIdForWrite(Integer rankId) {
        if (rankId != null) {
            membershipRankRepository.findById(rankId)
                    .orElseThrow(() -> new RuntimeException("rankId không tồn tại: " + rankId));
            return rankId;
        }
        MembershipRank defaultRank = membershipRankRepository.findAll().stream()
                .min(Comparator.comparing(r -> r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                .orElseThrow(() -> new RuntimeException("Chưa có dữ liệu hạng thành viên"));
        return defaultRank.getRankId();
    }

    private MembershipRank resolveRankEntityForRead(Integer rankId) {
        if (rankId != null) {
            MembershipRank fromUserRankId = membershipRankRepository.findById(rankId).orElse(null);
            if (fromUserRankId != null) {
                return fromUserRankId;
            }
        }
        return membershipRankRepository.findAll().stream()
                .min(Comparator.comparing(r -> r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                .orElse(null);
    }
}
