package com.fpoly.duan.service;

import com.fpoly.duan.dto.CustomerBasicDTO;
import com.fpoly.duan.dto.OrderDetailDTO;
import com.fpoly.duan.dto.ProductSoldBreakdown;
import com.fpoly.duan.dto.RevenueBreakdownDTO;
import com.fpoly.duan.dto.StaffDashboardStats;
import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.entity.OrderDetailFood;
import com.fpoly.duan.entity.Ticket;
import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.entity.StaffShift;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.entity.MembershipRank;
import com.fpoly.duan.repository.MembershipRankRepository;
import com.fpoly.duan.repository.OrderOnlineRepository;
import com.fpoly.duan.repository.TicketRepository;
import com.fpoly.duan.repository.OrderDetailFoodRepository;
import com.fpoly.duan.repository.StaffShiftRepository;
import com.fpoly.duan.repository.StaffRepository;
import com.fpoly.duan.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffDashboardService {
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final OrderOnlineRepository orderOnlineRepository;
    private final TicketRepository ticketRepository;
    private final OrderDetailFoodRepository orderDetailFoodRepository;
    private final StaffShiftRepository staffShiftRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MembershipRankRepository membershipRankRepository;

    public List<RevenueBreakdownDTO> getRevenueBreakdown(Integer staffId, Integer shiftId) {
        try {
            LocalDateTime[] range = getRange(staffId, shiftId);
            List<Object[]> rows = orderOnlineRepository.getRevenueBreakdownByStaffBetween(staffId, range[0], range[1]);
            List<RevenueBreakdownDTO> list = new ArrayList<>();
            for (Object[] row : rows) {
                String method = (row[0] != null) ? row[0].toString() : "N/A";
                Double total = 0.0;
                if (row[1] != null) {
                    if (row[1] instanceof Number) {
                        total = ((Number) row[1]).doubleValue();
                    }
                }
                list.add(RevenueBreakdownDTO.builder()
                        .method(method)
                        .total(total)
                        .build());
            }
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<ProductSoldBreakdown> getProductsBreakdown(Integer staffId, Integer shiftId) {
        try {
            LocalDateTime[] range = getRange(staffId, shiftId);
            List<Object[]> rows = orderDetailFoodRepository.getProductsBreakdownByStaffBetween(staffId, range[0], range[1]);
            List<ProductSoldBreakdown> list = new ArrayList<>();
            for (Object[] row : rows) {
                String name = (row[0] != null) ? row[0].toString() : "N/A";
                Long qty = 0L;
                if (row[1] != null) {
                    if (row[1] instanceof Number) {
                        qty = ((Number) row[1]).longValue();
                    }
                }
                list.add(ProductSoldBreakdown.builder()
                        .productName(name)
                        .totalQuantity(qty)
                        .build());
            }
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<OrderOnline> getRecentOrders(Integer staffId, Integer shiftId) {
        if (shiftId != null) {
            LocalDateTime[] range = getRange(staffId, shiftId);
            return orderOnlineRepository.findTop10ByStaffStaffIdAndCreatedAtBetweenOrderByCreatedAtDesc(staffId, range[0], range[1]);
        }
        return orderOnlineRepository.findTop10ByStaffStaffIdOrderByCreatedAtDesc(staffId);
    }

    public OrderDetailDTO getOrderDetail(String orderCode) {
        OrderOnline order = orderOnlineRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        List<Ticket> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId());
        List<OrderDetailFood> foods = orderDetailFoodRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId());

        List<OrderDetailDTO.TicketInfo> ticketInfos = tickets.stream().map(t -> {
            String seatLabel;
            if (t.getSeatLabel() != null) {
                seatLabel = t.getSeatLabel();
            } else if (t.getSeat() != null) {
                String row = (t.getSeat().getRow() != null) ? t.getSeat().getRow() : "";
                String num = (t.getSeat().getNumber() != null) ? t.getSeat().getNumber() : "";
                seatLabel = row + num;
            } else {
                seatLabel = "";
            }
            String qrToken = t.getQrToken();
            return OrderDetailDTO.TicketInfo.builder()
                .ticketId(t.getTicketId())
                .ticketCode(t.getTicketCode())
                .qrToken(qrToken)
                .qrImagePath(qrToken != null && !qrToken.isBlank() ? "/ticket-orders/qr/" + qrToken : null)
                .movieTitle(t.getShowtime().getMovie().getTitle())
                .showtime(t.getShowtime().getStartTime())
                .showtimeStart(t.getShowtime().getStartTime())
                .roomName(t.getShowtime().getRoom().getName())
                .seatNumber(seatLabel)
                .seatTypeName(t.getSeat() != null && t.getSeat().getSeatType() != null ? t.getSeat().getSeatType().getName() : "Ghế thường")
                .originalPrice(t.getOriginalPrice() != null ? t.getOriginalPrice() : t.getPrice())
                .promotionDiscount(t.getPromotionDiscount() != null ? t.getPromotionDiscount() : 0.0)
                .price(t.getPrice())
                .build();
        }).collect(Collectors.toList());

        List<OrderDetailDTO.FoodInfo> foodInfos = foods.stream().map(f -> 
            OrderDetailDTO.FoodInfo.builder()
                .productName(f.getProduct().getName())
                .quantity(f.getQuantity())
                .price(f.getPrice())
                .build()
        ).collect(Collectors.toList());

        Staff staff = order.getStaff();
        Cinema cinema = resolveCinema(order, tickets, staff);
        String voucherCode = order.getUserVoucher() != null && order.getUserVoucher().getVoucher() != null
                ? order.getUserVoucher().getVoucher().getCode()
                : null;

        return OrderDetailDTO.builder()
                .id(order.getOrderOnlineId())
                .orderCode(order.getOrderCode())
                .createdAt(order.getCreatedAt())
                .originalAmount(order.getOriginalAmount())
                .discountAmount(order.getDiscountAmount())
                .finalAmount(order.getFinalAmount())
                .paymentMethod(order.getPaymentMethod())
                .voucherCode(voucherCode)
                .status(order.getStatus())
                .customerName(order.getUser() != null ? order.getUser().getFullname() : "Khách vãng lai")
                .customerEmail(order.getUser() != null ? order.getUser().getEmail() : null)
                .customerPhone(order.getUser() != null ? order.getUser().getPhone() : null)
                .staffName(staff != null ? staff.getFullname() : "Đặt Online")
                .cinemaId(cinema != null ? cinema.getCinemaId() : null)
                .cinemaName(cinema != null ? cinema.getName() : null)
                .cinemaAddress(cinema != null ? cinema.getAddress() : null)
                .tickets(ticketInfos)
                .foods(foodInfos)
                .build();
    }

    private Cinema resolveCinema(OrderOnline order, List<Ticket> tickets, Staff staff) {
        if (order != null && order.getCinema() != null) {
            return order.getCinema();
        }
        if (staff != null && staff.getCinema() != null) {
            return staff.getCinema();
        }
        return tickets.stream()
                .filter(t -> t.getShowtime() != null
                        && t.getShowtime().getRoom() != null
                        && t.getShowtime().getRoom().getCinema() != null)
                .map(t -> t.getShowtime().getRoom().getCinema())
                .findFirst()
                .orElse(null);
    }

    public StaffDashboardStats getDashboardStats(Integer staffId, Integer shiftId) {
        try {
            LocalDateTime now = nowInAppZone();
            String cinemaName = "N/A";
            Optional<Staff> staffOpt = staffRepository.findById(staffId);
            if (staffOpt.isPresent() && staffOpt.get().getCinema() != null) {
                cinemaName = staffOpt.get().getCinema().getName();
            }

            LocalDateTime[] range = getRange(staffId, shiftId);
            String shiftName;
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");

            if (shiftId != null) {
                Optional<StaffShift> shiftOpt = staffShiftRepository.findById(shiftId);
                shiftName = shiftOpt.map(s -> "Ca: " + s.getStartTime().format(fmt) + " - " + s.getEndTime().format(fmt))
                        .orElse("Ca đã chọn");
            } else {
                Optional<StaffShift> currentShift = staffShiftRepository.findActiveShift(staffId, now);
                shiftName = currentShift.map(s -> "Ca: " + s.getStartTime().format(fmt) + " - " + s.getEndTime().format(fmt))
                        .orElse("Cả ngày hôm nay");
            }

            Double revenue = orderOnlineRepository.sumRevenueByStaffBetween(staffId, range[0], range[1]);
            Long tickets = ticketRepository.countTicketsByStaffBetweenJPQL(staffId, range[0], range[1]);
            Long products = orderDetailFoodRepository.countProductsByStaffBetween(staffId, range[0], range[1]);

            return StaffDashboardStats.builder()
                    .totalRevenue(revenue != null ? revenue : 0.0)
                    .totalTicketsSold(tickets != null ? tickets : 0L)
                    .totalProductsSold(products != null ? products : 0L)
                    .shiftName(shiftName)
                    .cinemaName(cinemaName)
                    .build();
        } catch (Exception e) {
            return StaffDashboardStats.builder()
                    .totalRevenue(0.0)
                    .totalTicketsSold(0L)
                    .totalProductsSold(0L)
                    .shiftName("Lỗi hệ thống")
                    .cinemaName("N/A")
                    .build();
        }
    }

    private LocalDateTime[] getRange(Integer staffId, Integer shiftId) {
        if (shiftId != null) {
            Optional<StaffShift> shiftOpt = staffShiftRepository.findById(shiftId);
            if (shiftOpt.isPresent()) {
                StaffShift shift = shiftOpt.get();
                if (shift.getStartTime() != null && shift.getEndTime() != null) {
                    return new LocalDateTime[]{shift.getStartTime(), shift.getEndTime()};
                }
            }
        }
        LocalDateTime now = nowInAppZone();
        return new LocalDateTime[]{now.toLocalDate().atStartOfDay(), now.toLocalDate().atTime(23, 59, 59)};
    }

    private LocalDateTime nowInAppZone() {
        return LocalDateTime.now(APP_ZONE);
    }

    public CustomerBasicDTO lookupCustomerByPhone(String phone) {
        Optional<User> existing = userRepository.findByPhone(phone);
        if (existing.isPresent()) {
            User u = existing.get();
            return CustomerBasicDTO.builder()
                    .userId(u.getUserId())
                    .fullname(u.getFullname())
                    .phone(u.getPhone())
                    .email(u.getEmail())
                    .isNew(false)
                    .build();
        }
        return CustomerBasicDTO.builder()
                .phone(phone)
                .isNew(true)
                .build();
    }

    public CustomerBasicDTO createGuestCustomer(String phone) {
        Optional<User> existing = userRepository.findByPhone(phone);
        if (existing.isPresent()) {
            User u = existing.get();
            return CustomerBasicDTO.builder()
                    .userId(u.getUserId())
                    .fullname(u.getFullname())
                    .phone(u.getPhone())
                    .email(u.getEmail())
                    .isNew(false)
                    .build();
        }
        Integer defaultRankId = membershipRankRepository.findAll().stream()
                .min(Comparator.comparing(r -> r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                .map(MembershipRank::getRankId)
                .orElse(null);

        User newUser = new User();
        newUser.setPhone(phone);
        newUser.setFullname("Khách " + phone);
        newUser.setEmail(phone + "@guest.local");
        newUser.setPassword(passwordEncoder.encode(phone));
        newUser.setStatus(1);
        newUser.setPoints(0);
        newUser.setTotalSpending(0.0);
        newUser.setRankId(defaultRankId);
        User saved = userRepository.save(newUser);
        return CustomerBasicDTO.builder()
                .userId(saved.getUserId())
                .fullname(saved.getFullname())
                .phone(saved.getPhone())
                .email(saved.getEmail())
                .isNew(true)
                .build();
    }
}
