package com.fpoly.duan.service;

import com.fpoly.duan.entity.Cinema;
import com.fpoly.duan.entity.OrderOnline;
import com.fpoly.duan.entity.Showtime;
import com.fpoly.duan.entity.Ticket;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tạo mã vé và QR do BE kiểm soát; QR không chứa thông tin khách ở dạng đọc được. */
@Service
public class TicketQrService {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] secret;

    public TicketQrService(@Value("${app.ticket-qr.secret:${app.jwt.secret:}}") String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            throw new IllegalStateException("Thiếu TICKET_QR_SECRET hoặc JWT_SECRET để mã hóa QR vé");
        }
        this.secret = configuredSecret.trim().getBytes(StandardCharsets.UTF_8);
    }

    /** Gọi sau khi vé đã được lưu để có ticketId. */
    public void assignSecureCodes(List<Ticket> tickets) {
        for (Ticket ticket : tickets) {
            if (ticket.getTicketId() == null) {
                throw new IllegalArgumentException("Phải lưu vé trước khi tạo QR");
            }
            String material = canonicalMaterial(ticket);
            String code = "TKT-" + base64Url(hmac(material)).substring(0, 24).toUpperCase(java.util.Locale.ROOT);
            ticket.setTicketCode(code);
            ticket.setQrToken(encrypt(serialize(ticket, code)));
        }
    }

    public byte[] toPng(String qrToken) {
        return toQrPng(qrToken, 360);
    }

    /** Sinh ảnh QR PNG cho nội dung thanh toán/đối soát do BE nhận được, không gọi dịch vụ QR bên ngoài. */
    public byte[] toPaymentQrPng(String content) {
        return toQrPng(content, 360);
    }

    private byte[] toQrPng(String content, int size) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Nội dung QR không được trống");
        }
        try {
            Map<EncodeHintType, Object> hints = new LinkedHashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(content.trim(), BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | java.io.IOException e) {
            throw new IllegalStateException("Không thể tạo ảnh QR vé", e);
        }
    }

    /** Mã vạch Code 128 của hóa đơn; dữ liệu là HMAC ngắn, không phải orderCode đọc được. */
    public byte[] toBarcodePng(String receiptToken) {
        try {
            Map<EncodeHintType, Object> hints = new LinkedHashMap<>();
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new com.google.zxing.oned.Code128Writer()
                    .encode(receiptToken, BarcodeFormat.CODE_128, 680, 150, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Không thể tạo mã vạch hóa đơn", e);
        }
    }

    /** Gọi sau khi order đã có ID. Mã HMAC bao gồm chi nhánh, giá trị, thời gian và mã đơn nhưng không thể dịch ngược. */
    public void assignReceiptToken(OrderOnline order) {
        if (order == null || order.getOrderOnlineId() == null) {
            throw new IllegalArgumentException("Phải lưu hóa đơn trước khi tạo mã vạch");
        }
        Cinema cinema = order.getCinema();
        String payload = String.join("|", "receipt-v1", value(order.getOrderOnlineId()), value(order.getOrderCode()),
                value(cinema != null ? cinema.getCinemaId() : null), value(cinema != null ? cinema.getName() : null),
                value(cinema != null ? cinema.getAddress() : null), value(order.getFinalAmount()), value(order.getCreatedAt()));
        order.setReceiptToken("RCP-" + base64Url(hmac(payload)).substring(0, 24).toUpperCase(java.util.Locale.ROOT));
    }

    /** Giải mã tối thiểu để API quét QR có thể tìm đúng vé; dữ liệu còn lại vẫn được đối chiếu với DB. */
    public TicketReference decodeReference(String qrToken) {
        try {
            byte[] combined = Base64.getUrlDecoder().decode(qrToken);
            if (combined.length <= 12) throw new IllegalArgumentException("QR không hợp lệ");
            byte[] iv = java.util.Arrays.copyOfRange(combined, 0, 12);
            byte[] cipherText = java.util.Arrays.copyOfRange(combined, 12, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(sha256(secret), "AES"), new GCMParameterSpec(128, iv));
            String plaintext = new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
            String[] parts = plaintext.split("\\|", 4);
            if (parts.length < 3 || !"v1".equals(parts[0])) throw new IllegalArgumentException("QR không đúng phiên bản");
            return new TicketReference(Integer.valueOf(parts[1]), parts[2]);
        } catch (Exception e) {
            throw new IllegalArgumentException("QR vé không hợp lệ hoặc đã bị thay đổi", e);
        }
    }

    public boolean matchesExpectedCode(Ticket ticket) {
        String expected = "TKT-" + base64Url(hmac(canonicalMaterial(ticket))).substring(0, 24).toUpperCase(java.util.Locale.ROOT);
        return ticket.getTicketCode() != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), ticket.getTicketCode().getBytes(StandardCharsets.UTF_8));
    }

    private String canonicalMaterial(Ticket ticket) {
        Showtime st = ticket.getShowtime();
        Cinema cinema = st != null && st.getRoom() != null ? st.getRoom().getCinema() : null;
        return String.join("|",
                value(ticket.getTicketId()),
                value(cinema != null ? cinema.getName() : null),
                value(cinema != null ? cinema.getAddress() : null),
                value(st != null && st.getMovie() != null ? st.getMovie().getTitle() : null),
                value(st != null ? st.getStartTime() : null),
                value(ticket.getSeat() != null ? ticket.getSeat().getRow() : null),
                value(ticket.getSeat() != null ? ticket.getSeat().getNumber() : null));
    }

    private String serialize(Ticket ticket, String code) {
        Showtime st = ticket.getShowtime();
        Cinema cinema = st != null && st.getRoom() != null ? st.getRoom().getCinema() : null;
        String seat = ticket.getSeat() == null ? "" : value(ticket.getSeat().getRow()) + value(ticket.getSeat().getNumber());
        return String.join("|", "v1", value(ticket.getTicketId()), code,
                value(cinema != null ? cinema.getName() : null), value(cinema != null ? cinema.getAddress() : null),
                value(st != null && st.getMovie() != null ? st.getMovie().getTitle() : null),
                value(st != null ? st.getStartTime() : null), seat);
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(sha256(secret), "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Không thể ký mã vé", e);
        }
    }

    private String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(sha256(secret), "AES"), new GCMParameterSpec(128, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return base64Url(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể mã hóa QR vé", e);
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String value(Object value) {
        if (value == null) return "";
        if (value instanceof java.time.LocalDateTime time) return TIME_FORMAT.format(time);
        return String.valueOf(value).trim();
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public record TicketReference(Integer ticketId, String ticketCode) {}
}
