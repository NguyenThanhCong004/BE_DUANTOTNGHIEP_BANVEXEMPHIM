package com.fpoly.duan.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fpoly.duan.dto.FoodOrderItemDTO;
import com.fpoly.duan.dto.FoodOrderVerificationDTO;
import com.fpoly.duan.entity.OrderDetailFood;
import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.repository.OrderDetailFoodRepository;
import com.fpoly.duan.repository.OrderOnlineRepository;

import lombok.RequiredArgsConstructor;

/** Soát QR bắp nước tại quầy — cùng cách với soát vé (quét là xác nhận giao hàng luôn một bước). */
@Service
@RequiredArgsConstructor
public class FoodOrderVerificationService {
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final OrderOnlineRepository orderOnlineRepository;
    private final OrderDetailFoodRepository orderDetailFoodRepository;

    @Transactional
    public FoodOrderVerificationDTO verify(Staff staff, String receiptToken) {
        OrderOnline order = orderOnlineRepository.findByReceiptToken(receiptToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn với mã QR này"));

        if (order.getStatus() == null || order.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn chưa thanh toán hoặc đã bị hủy");
        }
        if (staff.getCinema() != null && staff.getCinema().getCinemaId() != null
                && order.getCinema() != null && order.getCinema().getCinemaId() != null
                && !staff.getCinema().getCinemaId().equals(order.getCinema().getCinemaId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Đơn không thuộc rạp bạn đang làm việc");
        }

        List<OrderDetailFood> foods = orderDetailFoodRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId());
        if (foods.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Đơn này không có bắp nước để giao");
        }

        if (order.getFoodDeliveredAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Đơn đã được giao lúc " + order.getFoodDeliveredAt().format(DISPLAY_FORMAT));
        }
        order.setFoodDeliveredAt(LocalDateTime.now());
        orderOnlineRepository.save(order);

        return buildDto(order, foods);
    }

    private FoodOrderVerificationDTO buildDto(OrderOnline order, List<OrderDetailFood> foods) {
        List<FoodOrderItemDTO> items = foods.stream()
                .map(f -> FoodOrderItemDTO.builder()
                        .productName(f.getProduct() != null ? f.getProduct().getName() : "Sản phẩm")
                        .quantity(f.getQuantity())
                        .unitPrice(f.getPrice())
                        .build())
                .toList();

        return FoodOrderVerificationDTO.builder()
                .orderCode(order.getOrderCode())
                .customerName(order.getUser() != null ? order.getUser().getFullname() : "Khách vãng lai")
                .cinemaName(order.getCinema() != null ? order.getCinema().getName() : null)
                .items(items)
                .totalAmount(order.getFinalAmount())
                .delivered(true)
                .deliveredAt(order.getFoodDeliveredAt() != null ? order.getFoodDeliveredAt().format(DISPLAY_FORMAT) : null)
                .build();
    }
}
