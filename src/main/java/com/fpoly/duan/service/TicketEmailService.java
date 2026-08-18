package com.fpoly.duan.service;

import com.fpoly.duan.dto.TicketRescheduleSnapshotDTO;
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
import java.util.HashMap;
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

    /** Đơn được dời sang suất khác do phòng chiếu đóng tạm thời — QR mới bắt buộc vì QR cũ gắn chết suất/ghế cũ. */
    public void sendTicketRescheduledEmail(OrderOnline order, List<Ticket> newTickets,
            List<TicketRescheduleSnapshotDTO> oldSnapshots) {
        if (order.getUser() == null || order.getUser().getEmail() == null || order.getUser().getEmail().isBlank()) return;
        if (!emailService.isConfigured()) {
            log.warn("Không gửi được email dời vé cho đơn {}: MAIL_USERNAME/MAIL_PASSWORD chưa cấu hình", order.getOrderCode());
            return;
        }
        try {
            Map<Integer, TicketRescheduleSnapshotDTO> oldByTicketId = new HashMap<>();
            for (TicketRescheduleSnapshotDTO snap : oldSnapshots) {
                oldByTicketId.put(snap.getTicketId(), snap);
            }
            Map<String, byte[]> images = new LinkedHashMap<>();
            StringBuilder rows = new StringBuilder();
            int index = 1;
            for (Ticket ticket : newTickets) {
                if (ticket.getQrToken() == null || ticket.getTicketCode() == null) continue;
                String cid = "ticket-qr-" + ticket.getTicketId();
                images.put(cid, ticketQrService.toPng(ticket.getQrToken()));
                Showtime st = ticket.getShowtime();
                String movie = st != null && st.getMovie() != null ? st.getMovie().getTitle() : "Vé xem phim";
                String newShowtime = st != null && st.getStartTime() != null ? TIME_FORMAT.format(st.getStartTime()) : "";
                String newSeat = ticket.getSeat() == null ? "" : safe(ticket.getSeat().getRow()) + safe(ticket.getSeat().getNumber());
                TicketRescheduleSnapshotDTO old = oldByTicketId.get(ticket.getTicketId());
                String oldShowtime = old != null ? safe(old.getShowtimeStart()) : "";
                String oldSeat = old != null ? safe(old.getSeatLabel()) : "";
                rows.append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='margin:0 0 18px;background:rgba(255,255,255,0.04);border:1px solid rgba(255,255,255,0.08);border-radius:12px;'><tr>")
                        .append("<td style='padding:18px;vertical-align:top;'>")
                        .append("<p style='margin:0 0 10px;font-size:15px;font-weight:800;color:#d4ff00;'>Vé ").append(index++).append(" &middot; ").append(escape(movie)).append("</p>")
                        .append("<p style='margin:0;font-size:13px;line-height:1.8;color:#f0f0ff;'>")
                        .append("<b>Mã vé mới:</b> ").append(escape(ticket.getTicketCode())).append("<br>")
                        .append("<b>Suất/ghế cũ:</b> <span style='text-decoration:line-through;color:rgba(240,240,255,0.5);'>")
                        .append(escape(oldShowtime)).append(" &middot; ").append(escape(oldSeat)).append("</span><br>")
                        .append("<b>Suất/ghế mới:</b> <span style='color:#d4ff00;font-weight:700;'>")
                        .append(escape(newShowtime)).append(" &middot; ").append(escape(newSeat)).append("</span></p>")
                        .append("</td>")
                        .append("<td style='padding:18px;width:120px;'><img src='cid:").append(cid).append("' width='110' height='110' alt='QR vé' style='background:#ffffff;border-radius:8px;padding:6px;display:block;'></td>")
                        .append("</tr></table>");
            }
            if (images.isEmpty()) return;
            String body = "<p style=\"margin:0 0 16px;\">Kính gửi Quý khách,</p>"
                    + "<p style=\"margin:0 0 20px;\">Do sự cố kỹ thuật ngoài ý muốn tại phòng chiếu, MovieZone <strong>thành thật xin lỗi</strong> "
                    + "và đã chủ động dời lịch chiếu cho đơn <strong>" + escape(order.getOrderCode()) + "</strong> sang suất chiếu mới như dưới đây. "
                    + "Vé cũ không còn hiệu lực — vui lòng dùng mã QR mới khi ra rạp. Giá vé giữ nguyên như đã thanh toán.</p>" + rows
                    + "<p style=\"margin:20px 0 0;\">Rất mong Quý khách thông cảm cho sự bất tiện này. Mọi thắc mắc vui lòng liên hệ hotline rạp.</p>";
            String html = EmailBrandKit.wrap("Đơn " + order.getOrderCode() + " đã được dời sang suất chiếu mới", body);
            emailService.sendHtmlWithInlineImages(order.getUser().getEmail(),
                    "[MovieZone] Xin lỗi Quý khách — đơn " + order.getOrderCode() + " đã được dời suất chiếu", html, images);
        } catch (Exception e) {
            log.error("Gửi email dời vé thất bại cho đơn {}", order.getOrderCode(), e);
        }
    }

    /** Đơn bị hủy do phòng chiếu đóng và không tìm được suất thay thế phù hợp — hoàn tiền xử lý thủ công ngoài hệ thống. */
    public void sendOrderCancelledForClosureEmail(OrderOnline order, List<TicketRescheduleSnapshotDTO> oldSnapshots) {
        if (order.getUser() == null || order.getUser().getEmail() == null || order.getUser().getEmail().isBlank()) return;
        if (!emailService.isConfigured()) {
            log.warn("Không gửi được email hủy đơn (đóng phòng) cho đơn {}: MAIL_USERNAME/MAIL_PASSWORD chưa cấu hình", order.getOrderCode());
            return;
        }
        try {
            StringBuilder rows = new StringBuilder("<ul style='margin:0;padding-left:18px;color:#f0f0ff;font-size:13px;line-height:1.9;'>");
            for (TicketRescheduleSnapshotDTO snap : oldSnapshots) {
                rows.append("<li>").append(escape(snap.getMovieTitle())).append(" &middot; ")
                        .append(escape(snap.getShowtimeStart())).append(" &middot; Ghế ")
                        .append(escape(snap.getSeatLabel())).append("</li>");
            }
            rows.append("</ul>");
            String body = "<p style=\"margin:0 0 16px;\">Kính gửi Quý khách,</p>"
                    + "<p style=\"margin:0 0 16px;\">Do sự cố kỹ thuật ngoài ý muốn tại phòng chiếu, MovieZone <strong>thành thật xin lỗi</strong> "
                    + "và rất tiếc phải hủy đơn <strong>" + escape(order.getOrderCode()) + "</strong> dưới đây vì không còn suất chiếu phù hợp để thay thế:</p>"
                    + rows
                    + "<p style=\"margin:20px 0 0;\">Rạp sẽ chủ động liên hệ với Quý khách để hoàn lại số tiền đã thanh toán trong thời gian sớm nhất. "
                    + "Rất mong Quý khách thông cảm cho sự bất tiện này.</p>";
            String html = EmailBrandKit.wrap("Đơn " + order.getOrderCode() + " đã được hủy do sự cố phòng chiếu", body);
            emailService.sendHtml(order.getUser().getEmail(),
                    "[MovieZone] Xin lỗi Quý khách — đơn " + order.getOrderCode() + " đã bị hủy do sự cố phòng chiếu", html);
        } catch (Exception e) {
            log.error("Gửi email hủy đơn (đóng phòng) thất bại cho đơn {}", order.getOrderCode(), e);
        }
    }

    private static String safe(Object value) { return value == null ? "" : String.valueOf(value); }
    private static String escape(String value) {
        return safe(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
