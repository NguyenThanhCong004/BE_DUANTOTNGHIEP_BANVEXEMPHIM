package com.fpoly.duan.service;

import com.fpoly.duan.dto.TicketQrVerificationDTO;
import com.fpoly.duan.entity.Staff;
import com.fpoly.duan.entity.Showtime;
import com.fpoly.duan.entity.Ticket;
import com.fpoly.duan.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TicketVerificationService {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final TicketRepository ticketRepository;
    private final TicketQrService ticketQrService;

    public TicketQrVerificationDTO verify(Staff staff, String qrToken) {
        TicketQrService.TicketReference ref = ticketQrService.decodeReference(qrToken);
        Ticket ticket = ticketRepository.findById(ref.ticketId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy vé"));
        if (ticket.getQrToken() == null || !MessageDigest.isEqual(
                ticket.getQrToken().getBytes(StandardCharsets.UTF_8), qrToken.getBytes(StandardCharsets.UTF_8))
                || !ref.ticketCode().equals(ticket.getTicketCode()) || !ticketQrService.matchesExpectedCode(ticket)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QR vé không hợp lệ hoặc đã bị thay đổi");
        }
        Showtime st = ticket.getShowtime();
        if (st == null || st.getRoom() == null || st.getRoom().getCinema() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vé thiếu thông tin rạp");
        }
        if (staff.getCinema() != null && staff.getCinema().getCinemaId() != null
                && !staff.getCinema().getCinemaId().equals(st.getRoom().getCinema().getCinemaId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vé không thuộc rạp bạn đang làm việc");
        }
        if (ticket.getStatus() == null || ticket.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vé chưa thanh toán hoặc đã bị hủy");
        }
        if (ticket.getCheckedInAt() != null) {
            String usedAt = ticket.getCheckedInAt().format(FMT);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Vé đã được sử dụng lúc " + usedAt);
        }
        // Kiểm tra khung giờ hợp lệ: cho phép vào 30 phút trước đến 3 giờ sau suất bắt đầu (giờ VN)
        LocalDateTime nowVN = LocalDateTime.now(VN);
        if (st.getStartTime() != null) {
            LocalDateTime openGate  = st.getStartTime().minusMinutes(30);
            LocalDateTime closeGate = st.getStartTime().plusHours(3);
            if (nowVN.isBefore(openGate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Suất chiếu chưa đến giờ vào rạp. Cổng mở lúc " + openGate.format(FMT));
            }
            if (nowVN.isAfter(closeGate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Suất chiếu " + st.getStartTime().format(FMT) + " đã kết thúc, không thể soát vé");
            }
        }
        ticket.setCheckedInAt(nowVN);
        ticketRepository.save(ticket);
        List<Ticket> orderTickets = ticket.getOrderOnline() != null
                ? ticketRepository.findByOrderOnline_OrderOnlineId(ticket.getOrderOnline().getOrderOnlineId())
                : List.of(ticket);
        String seats = orderTickets.stream()
                .filter(t -> t.getShowtime() != null
                        && t.getShowtime().getShowtimeId() != null
                        && t.getShowtime().getShowtimeId().equals(st.getShowtimeId())
                        && t.getSeat() != null)
                .sorted(Comparator
                        .comparing((Ticket t) -> string(t.getSeat().getRow()))
                        .thenComparing(t -> t.getSeat().getX() != null ? t.getSeat().getX() : 0)
                        .thenComparing(t -> string(t.getSeat().getNumber())))
                .map(t -> string(t.getSeat().getRow()) + string(t.getSeat().getNumber()))
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElseGet(() -> ticket.getSeat() == null ? "" : string(ticket.getSeat().getRow()) + string(ticket.getSeat().getNumber()));
        String checkedInAtStr = ticket.getCheckedInAt() != null
                ? ticket.getCheckedInAt().format(FMT) : null;
        return TicketQrVerificationDTO.builder()
                .orderCode(ticket.getOrderOnline() != null ? ticket.getOrderOnline().getOrderCode() : null)
                .ticketCode(ticket.getTicketCode())
                .status(ticket.getStatus())
                .customerName(ticket.getOrderOnline() != null && ticket.getOrderOnline().getUser() != null
                        ? ticket.getOrderOnline().getUser().getFullname() : "Khách vãng lai")
                .cinemaName(st.getRoom().getCinema().getName())
                .cinemaAddress(st.getRoom().getCinema().getAddress())
                .movieTitle(st.getMovie() != null ? st.getMovie().getTitle() : "Vé xem phim")
                .showtime(st.getStartTime() != null ? st.getStartTime().format(FMT) : "")
                .roomName(st.getRoom().getName())
                .seatNumber(seats)
                .checkedInAt(checkedInAtStr)
                .build();
    }

    private static String string(Object value) { return value == null ? "" : String.valueOf(value); }
}
