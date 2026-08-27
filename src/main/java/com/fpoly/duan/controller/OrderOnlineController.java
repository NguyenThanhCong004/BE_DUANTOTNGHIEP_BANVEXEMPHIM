package com.fpoly.duan.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.duan.config.OpenApiConfig;
import com.fpoly.duan.dto.ApiResponse;
import com.fpoly.duan.dto.OrderOnlineDTO;
import com.fpoly.duan.entity.MembershipRank;
import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.entity.User;
import com.fpoly.duan.entity.Voucher;
import com.fpoly.duan.repository.MembershipRankRepository;
import com.fpoly.duan.repository.OrderDetailFoodRepository;
import com.fpoly.duan.repository.OrderOnlineRepository;
import com.fpoly.duan.repository.TicketRepository;
import com.fpoly.duan.service.CinemaScopeService;
import com.fpoly.duan.util.SearchUtils;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/orders-online")
@CrossOrigin(origins = "*", maxAge = 3600)
@Tag(name = "Table: order_online", description = "Danh sách và quản lý đơn đặt online (bảng order_online).")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
@RequiredArgsConstructor
public class OrderOnlineController {

    private final OrderOnlineRepository orderOnlineRepository;
    private final TicketRepository ticketRepository;
    private final OrderDetailFoodRepository orderDetailFoodRepository;
    private final CinemaScopeService cinemaScopeService;
    private final MembershipRankRepository membershipRankRepository;

    @GetMapping
    @Operation(summary = "Danh sách đơn online (có lọc theo rạp)")
    public ResponseEntity<ApiResponse<List<OrderOnlineDTO>>> list(
            @RequestParam(required = false) Integer cinemaId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String q) {
        Integer effectiveCinemaId = cinemaScopeService.effectiveCinemaId(cinemaId);
        String term = SearchUtils.pick(search, keyword, q);
        List<OrderOnline> orders;
        if (effectiveCinemaId != null) {
            orders = orderOnlineRepository.findAll().stream()
                    .filter(o -> effectiveCinemaId.equals(resolveCinemaId(o)))
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .collect(Collectors.toList());
        } else {
            orders = orderOnlineRepository.findAll().stream()
                    .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                    .collect(Collectors.toList());
        }

        List<OrderOnlineDTO> data = orders.stream()
                .map(this::toDTO)
                .filter(o -> SearchUtils.matches(term,
                        o.getId(), o.getOrderCode(), o.getCustomerName(), o.getCustomerEmail(),
                        o.getCinemaName(), o.getCinemaAddress(), o.getStaffName(), o.getVoucherCode(),
                        o.getStatus(), o.getFinalAmount(),
                        o.getTickets() != null ? o.getTickets().stream()
                                .map(OrderOnlineDTO.TicketInfoDTO::getMovieTitle)
                                .collect(Collectors.joining(" ")) : null,
                        o.getFoods() != null ? o.getFoods().stream()
                                .map(OrderOnlineDTO.FoodInfoDTO::getProductName)
                                .collect(Collectors.joining(" ")) : null))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.<List<OrderOnlineDTO>>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(data)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết đơn")
    public ResponseEntity<ApiResponse<OrderOnlineDTO>> getById(@PathVariable Integer id) {
        OrderOnline o = orderOnlineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn với id: " + id));
        requireOrderScope(o);
        return ResponseEntity.ok(ApiResponse.<OrderOnlineDTO>builder()
                .status(HttpStatus.OK.value())
                .message("OK")
                .data(toDTO(o))
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa đơn online (admin)")
    public ResponseEntity<ApiResponse<Void>> deleteOrderOnline(@PathVariable Integer id) {
        OrderOnline order = orderOnlineRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn với id: " + id));
        requireOrderScope(order);
        orderOnlineRepository.delete(order);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .status(HttpStatus.OK.value())
                .message("Đã xóa đơn")
                .data(null)
                .build());
    }

    private OrderOnlineDTO toDTO(OrderOnline o) {
        User u = o.getUser();
        Staff s = o.getStaff();

        String name = u != null ? (u.getFullname() != null && !u.getFullname().isBlank()
                ? u.getFullname()
                : u.getEmail()) : "Khách vãng lai";
        String email = u != null && u.getEmail() != null ? u.getEmail() : "—";
        String phone = u != null && u.getPhone() != null ? u.getPhone() : "—";
        Voucher voucher = o.getUserVoucher() != null ? o.getUserVoucher().getVoucher() : null;
        String voucherCode = voucher != null ? voucher.getCode() : null;
        String voucherDiscountType = voucher != null ? voucher.getDiscountType() : null;
        Double voucherValue = voucher != null ? voucher.getValue() : null;

        MembershipRank rank = null;
        if (u != null) {
            if (u.getRankId() != null) {
                rank = membershipRankRepository.findById(u.getRankId()).orElse(null);
            }
            // Fallback: user tồn tại nhưng rank_id chưa được gán → dùng hạng thấp nhất
            if (rank == null) {
                rank = membershipRankRepository.findAll().stream()
                        .filter(r -> r.getStatus() == null || r.getStatus() == 1)
                        .min(java.util.Comparator.comparingDouble(
                                r -> r.getMinSpending() != null ? r.getMinSpending() : 0.0))
                        .orElse(null);
            }
        }
        String rankName = rank != null ? rank.getRankName() : null;
        Double rankDiscountPercent = rank != null ? rank.getDiscountPercent() : null;

        // Lấy thông tin vé — tách rõ phần giảm do khuyến mãi và phần giảm do hạng hội viên
        // (backend chỉ lưu originalPrice/promotionDiscount/price, phần hội viên được suy ra từ hiệu số).
        List<OrderOnlineDTO.TicketInfoDTO> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(o.getOrderOnlineId())
                .stream().map(t -> {
                    String qrToken = t.getQrToken();
                    double original = t.getOriginalPrice() != null ? t.getOriginalPrice() : (t.getPrice() != null ? t.getPrice() : 0.0);
                    double promoDiscount = t.getPromotionDiscount() != null ? t.getPromotionDiscount() : 0.0;
                    double finalPrice = t.getPrice() != null ? t.getPrice() : 0.0;
                    double afterPromo = original - promoDiscount;
                    double memberDiscount = Math.max(0.0, afterPromo - finalPrice);
                    double promoPercent = original > 0 ? Math.round(promoDiscount / original * 1000.0) / 10.0 : 0.0;
                    double memberPercent = afterPromo > 0 ? Math.round(memberDiscount / afterPromo * 1000.0) / 10.0 : 0.0;

                    return OrderOnlineDTO.TicketInfoDTO.builder()
                            .ticketId(t.getTicketId())
                            .ticketCode(t.getTicketCode())
                            .qrToken(qrToken)
                            .qrImagePath(qrToken != null && !qrToken.isBlank() ? "/ticket-orders/qr/" + qrToken : null)
                            .movieTitle(t.getShowtime() != null && t.getShowtime().getMovie() != null
                                    ? t.getShowtime().getMovie().getTitle()
                                    : "N/A")
                            .showtime(t.getShowtime() != null ? t.getShowtime().getStartTime() : null)
                            .roomName(t.getShowtime() != null && t.getShowtime().getRoom() != null
                                    ? t.getShowtime().getRoom().getName()
                                    : "N/A")
                            .seatNumber(t.getSeatLabel() != null ? t.getSeatLabel()
                                    : t.getSeat() != null ? (t.getSeat().getRow() + t.getSeat().getNumber())
                                    : "N/A")
                            .seatTypeName(t.getSeat() != null && t.getSeat().getSeatType() != null
                                    ? t.getSeat().getSeatType().getName()
                                    : "N/A")
                            .originalPrice(original)
                            .promotionDiscount(promoDiscount)
                            .promotionDiscountPercent(promoPercent)
                            .membershipDiscount(memberDiscount)
                            .membershipDiscountPercent(memberPercent)
                            .price(finalPrice)
                            .build();
                })
                .collect(Collectors.toList());

        // Lấy thông tin đồ ăn
        List<OrderOnlineDTO.FoodInfoDTO> foods = orderDetailFoodRepository.findByOrderOnline_OrderOnlineId(o.getOrderOnlineId())
                .stream().map(f -> OrderOnlineDTO.FoodInfoDTO.builder()
                        .productName(f.getProduct() != null ? f.getProduct().getName() : "N/A")
                        .quantity(f.getQuantity())
                        .price(f.getPrice())
                        .build())
                .collect(Collectors.toList());

        // Tên rạp (Ưu tiên lấy từ trường cinema trực tiếp của đơn hàng)
        String cinemaName = "N/A";
        Integer cinemaId = null;
        String cinemaAddress = "N/A";
        
        if (o.getCinema() != null) {
            cinemaName = o.getCinema().getName();
            cinemaId = o.getCinema().getCinemaId();
            cinemaAddress = o.getCinema().getAddress();
        } else if (s != null && s.getCinema() != null) {
            // Dự phòng 1: Từ nhân viên (Dữ liệu cũ)
            cinemaName = s.getCinema().getName();
            cinemaId = s.getCinema().getCinemaId();
            cinemaAddress = s.getCinema().getAddress();
        } else {
            // Dự phòng 2: Từ vé phim (Dữ liệu cũ/Online)
            var allTickets = ticketRepository.findByOrderOnline_OrderOnlineId(o.getOrderOnlineId());
            if (!allTickets.isEmpty()) {
                var firstTicket = allTickets.get(0);
                if (firstTicket.getShowtime() != null && firstTicket.getShowtime().getRoom() != null
                        && firstTicket.getShowtime().getRoom().getCinema() != null) {
                    cinemaName = firstTicket.getShowtime().getRoom().getCinema().getName();
                    cinemaId = firstTicket.getShowtime().getRoom().getCinema().getCinemaId();
                    cinemaAddress = firstTicket.getShowtime().getRoom().getCinema().getAddress();
                }
            }
        }

        return OrderOnlineDTO.builder()
                .id(o.getOrderOnlineId())
                .orderCode(o.getOrderCode())
                .createdAt(o.getCreatedAt())
                .originalAmount(o.getOriginalAmount())
                .discountAmount(o.getDiscountAmount())
                .finalAmount(o.getFinalAmount())
                .status(o.getStatus())
                .userId(u != null ? u.getUserId() : null)
                .customerName(name)
                .customerEmail(email)
                .customerPhone(phone)
                .paymentMethod(o.getPaymentMethod())
                .voucherCode(voucherCode)
                .voucherDiscountType(voucherDiscountType)
                .voucherValue(voucherValue)
                .rankName(rankName)
                .rankDiscountPercent(rankDiscountPercent)
                .cinemaName(cinemaName)
                .cinemaId(cinemaId)
                .cinemaAddress(cinemaAddress)
                .staffName(s != null ? s.getFullname() : "Đặt Online")
                .tickets(tickets)
                .foods(foods)
                .build();
    }

    private void requireOrderScope(OrderOnline order) {
        Integer cinemaId = resolveCinemaId(order);
        if (cinemaId == null) {
            if (!cinemaScopeService.isSuperAdmin()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không xác định được rạp của đơn hàng");
            }
            return;
        }
        cinemaScopeService.requireCinemaAccess(cinemaId);
    }

    private Integer resolveCinemaId(OrderOnline o) {
        if (o == null) {
            return null;
        }
        if (o.getCinema() != null) {
            return o.getCinema().getCinemaId();
        }
        Integer idFromTicket = ticketRepository.findByOrderOnline_OrderOnlineId(o.getOrderOnlineId()).stream()
                .filter(t -> t.getShowtime() != null && t.getShowtime().getRoom() != null
                        && t.getShowtime().getRoom().getCinema() != null)
                .map(t -> t.getShowtime().getRoom().getCinema().getCinemaId())
                .findFirst()
                .orElse(null);
        if (idFromTicket != null) {
            return idFromTicket;
        }
        return o.getStaff() != null && o.getStaff().getCinema() != null
                ? o.getStaff().getCinema().getCinemaId()
                : null;
    }
}
