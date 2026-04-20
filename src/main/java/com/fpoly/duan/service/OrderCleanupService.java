package com.fpoly.duan.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.duan.entity.OrderDetailFood;
import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.entity.Ticket;
import com.fpoly.duan.repository.OrderDetailFoodRepository;
import com.fpoly.duan.repository.OrderOnlineRepository;
import com.fpoly.duan.repository.TicketRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderCleanupService {
    private static final int ORDER_STATUS_PENDING = 0;
    private static final int ORDER_STATUS_CANCELLED = 2;
    private static final int TICKET_STATUS_CANCELLED = 2;
    private static final int FOOD_STATUS_CANCELLED = 2;

    private final OrderOnlineRepository orderOnlineRepository;
    private final TicketRepository ticketRepository;
    private final OrderDetailFoodRepository orderDetailFoodRepository;
    private final EphemeralSeatHoldService ephemeralSeatHoldService;

    /**
     * Tự động dọn dẹp các đơn hàng PENDING (trạng thái 0) quá 15 phút.
     * Chạy mỗi 5 phút một lần.
     */
    @Scheduled(fixedRate = 300_000)
    @Transactional
    public void cleanupExpiredPendingOrders() {
        LocalDateTime expiryTime = LocalDateTime.now().minusMinutes(15);
        List<OrderOnline> expiredOrders = orderOnlineRepository.findAll().stream()
                .filter(o -> o.getStatus() != null && o.getStatus() == ORDER_STATUS_PENDING)
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isBefore(expiryTime))
                .collect(Collectors.toList());

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("Bắt đầu dọn dẹp {} đơn hàng PENDING quá hạn", expiredOrders.size());

        for (OrderOnline o : expiredOrders) {
            try {
                List<Ticket> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(o.getOrderOnlineId());
                List<OrderDetailFood> foods = orderDetailFoodRepository.findByOrderOnline_OrderOnlineId(o.getOrderOnlineId());

                Integer stId = tickets.stream()
                        .map(t -> t.getShowtime() != null ? t.getShowtime().getShowtimeId() : null)
                        .filter(java.util.Objects::nonNull)
                        .findFirst()
                        .orElse(null);
                Set<Integer> seatIds = tickets.stream()
                        .map(t -> t.getSeat() != null ? t.getSeat().getSeatId() : null)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toSet());

                o.setStatus(ORDER_STATUS_CANCELLED);
                orderOnlineRepository.save(o);

                for (Ticket t : tickets) {
                    t.setStatus(TICKET_STATUS_CANCELLED);
                }
                ticketRepository.saveAll(tickets);

                for (OrderDetailFood f : foods) {
                    f.setStatus(FOOD_STATUS_CANCELLED);
                }
                orderDetailFoodRepository.saveAll(foods);

                if (stId != null && !seatIds.isEmpty()) {
                    ephemeralSeatHoldService.releaseSeats(stId, seatIds);
                }
                log.info("Đã chuyển đơn quá hạn sang trạng thái hủy: ID={}, OrderCode={}", o.getOrderOnlineId(),
                        o.getOrderCode());
            } catch (Exception e) {
                log.error("Lỗi khi xóa đơn hàng quá hạn ID={}: {}", o.getOrderOnlineId(), e.getMessage());
            }
        }
    }
}
