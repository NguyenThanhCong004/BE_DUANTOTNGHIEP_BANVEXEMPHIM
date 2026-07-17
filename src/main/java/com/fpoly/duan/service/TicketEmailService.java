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
                rows.append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='margin:0 0 18px;background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.08);border-radius:12px;'><tr>")
                        .append("<td style='padding:18px;vertical-align:top;'>")
                        .append("<p style='margin:0 0 10px;font-size:15px;font-weight:800;color:#d4ff00;'>Vé ").append(index++).append(" &middot; ").append(escape(movie)).append("</p>")
                        .append("<p style='margin:0;font-size:13px;line-height:1.8;color:#f0f0ff;'>")
                        .append("<b>Mã vé:</b> ").append(escape(ticket.getTicketCode())).append("<br>")
                        .append("<b>Rạp:</b> ").append(escape(cinema)).append("<br>")
                        .append("<b>Địa chỉ:</b> ").append(escape(address)).append("<br>")
                        .append("<b>Suất chiếu:</b> ").append(escape(showtime)).append(" &middot; <b>Ghế:</b> ").append(escape(seat)).append("</p>")
                        .append("</td>")
                        .append("<td style='padding:18px;width:120px;'><img src='cid:").append(cid).append("' width='110' height='110' alt='QR vé' style='background:#ffffff;border-radius:8px;padding:6px;display:block;'></td>")
                        .append("</tr></table>");
            }
            if (images.isEmpty()) return;
            String body = "<p style=\"margin:0 0 20px;\">Thanh toán đơn <strong>" + escape(order.getOrderCode())
                    + "</strong> đã thành công. Vui lòng xuất trình đúng mã QR của từng vé tại quầy soát vé.</p>" + rows;
            String html = EmailBrandKit.wrap("Vé điện tử của bạn cho đơn " + order.getOrderCode(), body);
            emailService.sendHtmlWithInlineImages(order.getUser().getEmail(), "[MovieZone] Vé xem phim — đơn " + order.getOrderCode(), html, images);
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
