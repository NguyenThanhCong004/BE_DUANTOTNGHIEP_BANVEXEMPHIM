package com.fpoly.duan.service;

import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.entity.Showtime;
import com.fpoly.duan.entity.Ticket;
import com.fpoly.duan.repository.OrderOnlineRepository;
import com.fpoly.duan.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketEmailService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private final EmailService emailService;
    private final TicketRepository ticketRepository;
    private final OrderOnlineRepository orderOnlineRepository;
    private final TicketQrService ticketQrService;

    /** Lỗi email không được làm hỏng kết quả thanh toán đã xác thực với PayOS. */
    public void sendPaidTicketEmailIfNeeded(OrderOnline order) {
        if (order.getTicketEmailSentAt() != null || order.getUser() == null
                || order.getUser().getEmail() == null || order.getUser().getEmail().isBlank()) return;
        if (!emailService.isConfigured()) {
            log.warn("Không gửi email QR cho đơn {}: MAIL_USERNAME/MAIL_PASSWORD chưa cấu hình", order.getOrderCode());
            return;
        }
        List<Ticket> tickets = ticketRepository.findByOrderOnline_OrderOnlineId(order.getOrderOnlineId());
        if (tickets.isEmpty()) return;
        try {
            Map<String, byte[]> images = new LinkedHashMap<>();
            StringBuilder rows = new StringBuilder();
            int index = 1;
            for (Ticket ticket : tickets) {
                if (ticket.getQrToken() == null || ticket.getTicketCode() == null) continue;
                String cid = "ticket-qr-" + ticket.getTicketId();
                images.put(cid, ticketQrService.toPng(ticket.getQrToken()));
                Showtime st = ticket.getShowtime();
                String movie = st != null && st.getMovie() != null ? st.getMovie().getTitle() : "Vé xem phim";
                String cinema = st != null && st.getRoom() != null && st.getRoom().getCinema() != null
                        ? st.getRoom().getCinema().getName() : "";
                String address = st != null && st.getRoom() != null && st.getRoom().getCinema() != null
                        ? st.getRoom().getCinema().getAddress() : "";
                String showtime = st != null && st.getStartTime() != null ? TIME_FORMAT.format(st.getStartTime()) : "";
                String seat = ticket.getSeat() == null ? "" : safe(ticket.getSeat().getRow()) + safe(ticket.getSeat().getNumber());
                rows.append("<section style='margin:24px 0;padding:18px;border:1px solid #e5e7eb;border-radius:12px'>")
                        .append("<h3 style='margin:0 0 8px'>Vé ").append(index++).append(" — ").append(escape(movie)).append("</h3>")
                        .append("<p><b>Mã vé:</b> ").append(escape(ticket.getTicketCode())).append("<br>")
                        .append("<b>Rạp:</b> ").append(escape(cinema)).append("<br>")
                        .append("<b>Địa chỉ:</b> ").append(escape(address)).append("<br>")
                        .append("<b>Suất chiếu:</b> ").append(escape(showtime)).append(" · <b>Ghế:</b> ").append(escape(seat)).append("</p>")
                        .append("<img src='cid:").append(cid).append("' width='220' height='220' alt='QR vé'>")
                        .append("</section>");
            }
            if (images.isEmpty()) return;
            String html = "<div style='font-family:Arial,sans-serif;color:#111827;max-width:640px;margin:auto'>"
                    + "<h2>Vé xem phim điện tử</h2><p>Thanh toán đơn <b>" + escape(order.getOrderCode())
                    + "</b> đã thành công. Vui lòng xuất trình đúng QR của từng vé tại rạp.</p>" + rows + "</div>";
            emailService.sendHtmlWithInlineImages(order.getUser().getEmail(), "Vé xem phim — đơn " + order.getOrderCode(), html, images);
            order.setTicketEmailSentAt(LocalDateTime.now());
            orderOnlineRepository.save(order);
        } catch (Exception e) {
            log.error("Gửi email QR vé thất bại cho đơn {}", order.getOrderCode(), e);
        }
    }

    private static String safe(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String escape(String value) {
        return safe(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
