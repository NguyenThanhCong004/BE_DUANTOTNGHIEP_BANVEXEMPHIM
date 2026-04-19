package com.fpoly.duan.service;

import com.fpoly.duan.dto.OrderDetailDTO;
import com.fpoly.duan.dto.ProductSoldBreakdown;
import com.fpoly.duan.dto.RevenueBreakdownDTO;
import com.fpoly.duan.dto.StaffDashboardStats;
import com.fpoly.duan.entity.*;
import com.fpoly.duan.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StaffDashboardService {

    private final OrderOnlineRepository orderOnlineRepository;
    private final TicketRepository ticketRepository;
    private final OrderDetailFoodRepository orderDetailFoodRepository;
    private final StaffShiftRepository staffShiftRepository;
    private final StaffRepository staffRepository;

    public List<RevenueBreakdownDTO> getRevenueBreakdown(Integer staffId) {
        try {
            LocalDateTime[] range = getCurrentRange(staffId);
            List<Object[]> rows = orderOnlineRepository.getRevenueBreakdownByStaffBetween(staffId, range[0], range[1]);
            List<RevenueBreakdownDTO> list = new ArrayList<>();
            for (Object[] row : rows) {
                list.add(RevenueBreakdownDTO.builder()
                        .method(row[0] != null ? row[0].toString() : "N/A")
                        .total(row[1] != null ? Double.valueOf(row[1].toString()) : 0.0)
                        .build());
            }
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<ProductSoldBreakdown> getProductsBreakdown(Integer staffId) {
        try {
            LocalDateTime[] range = getCurrentRange(staffId);
            List<Object[]> rows = orderDetailFoodRepository.getProductsBreakdownByStaffBetween(staffId, range[0], range[1]);
            List<ProductSoldBreakdown> list = new ArrayList<>();
            for (Object[] row : rows) {
                list.add(ProductSoldBreakdown.builder()
                        .productName(row[0].toString())
                        .totalQuantity(Long.valueOf(row[1].toString()))
                        .build());
            }
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public List<OrderOnline> getRecentOrders(Integer staffId) {
        return orderOnlineRepository.findTop10ByStaff_StaffIdOrderByCreatedAtDesc(staffId);
    }

    public OrderDetailDTO getOrderDetail(String orderCode) {
        OrderOnline order = orderOnlineRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        List<Ticket> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId());
        List<OrderDetailFood> foods = orderDetailFoodRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId());

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        List<OrderDetailDTO.TicketInfo> ticketInfos = tickets.stream().map(t -> 
            OrderDetailDTO.TicketInfo.builder()
                .movieTitle(t.getShowtime().getMovie().getTitle())
                .showtime(t.getShowtime().getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")))
                .roomName(t.getShowtime().getRoom().getName())
                .seatNumber(t.getSeat().getNumber())
                .price(t.getPrice())
                .build()
        ).collect(Collectors.toList());

        List<OrderDetailDTO.FoodInfo> foodInfos = foods.stream().map(f -> 
            OrderDetailDTO.FoodInfo.builder()
                .productName(f.getProduct().getName())
                .quantity(f.getQuantity())
                .price(f.getPrice())
                .build()
        ).collect(Collectors.toList());

        return OrderDetailDTO.builder()
                .orderCode(order.getOrderCode())
                .createdAt(order.getCreatedAt().format(fmt))
                .finalAmount(order.getFinalAmount())
                .paymentMethod(order.getPaymentMethod())
                .customerName(order.getUser() != null ? order.getUser().getFullname() : "Khách vãng lai")
                .tickets(ticketInfos)
                .foods(foodInfos)
                .build();
    }

    public StaffDashboardStats getDashboardStats(Integer staffId) {
        LocalDateTime now = LocalDateTime.now();
        
        // Lấy tên rạp
        String cinemaName = "N/A";
        Optional<Staff> staffOpt = staffRepository.findById(staffId);
        if (staffOpt.isPresent() && staffOpt.get().getCinema() != null) {
            cinemaName = staffOpt.get().getCinema().getName();
        }

        Optional<StaffShift> currentShift = staffShiftRepository.findFirstByStaff_StaffIdAndStartTimeBeforeAndEndTimeAfter(
                staffId, now, now);

        LocalDateTime start;
        LocalDateTime end;
        String shiftName;

        if (currentShift.isPresent()) {
            StaffShift shift = currentShift.get();
            start = shift.getStartTime();
            end = shift.getEndTime();
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
            shiftName = "Ca: " + start.format(fmt) + " - " + end.format(fmt);
        } else {
            start = now.toLocalDate().atStartOfDay();
            end = now.toLocalDate().atTime(23, 59, 59);
            shiftName = "Cả ngày hôm nay";
        }

        try {
            Double revenue = orderOnlineRepository.sumRevenueByStaffBetween(staffId, start, end);
            Long tickets = ticketRepository.countTicketsByStaffBetweenJPQL(staffId, start, end);
            Long products = orderDetailFoodRepository.countProductsByStaffBetween(staffId, start, end);

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
                    .shiftName(shiftName + " (Lỗi)")
                    .cinemaName(cinemaName)
                    .build();
        }
    }

    private LocalDateTime[] getCurrentRange(Integer staffId) {
        LocalDateTime now = LocalDateTime.now();
        Optional<StaffShift> currentShift = staffShiftRepository.findFirstByStaff_StaffIdAndStartTimeBeforeAndEndTimeAfter(
                staffId, now, now);
        if (currentShift.isPresent()) {
            return new LocalDateTime[]{currentShift.get().getStartTime(), currentShift.get().getEndTime()};
        } else {
            return new LocalDateTime[]{now.toLocalDate().atStartOfDay(), now.toLocalDate().atTime(23, 59, 59)};
        }
    }
}
