package com.fpoly.duan.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * Gửi email qua cấu hình {@code spring.mail.*} (Gmail SMTP trong application.properties).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final String FROM_DISPLAY_NAME = "MovieZone";

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    public boolean isConfigured() {
        return fromAddress != null && !fromAddress.isBlank()
                && mailPassword != null && !mailPassword.isBlank();
    }

    private String requireFromAddress() {
        if (!isConfigured()) {
            throw new IllegalStateException("Chưa cấu hình MAIL_USERNAME/MAIL_PASSWORD để gửi email.");
        }
        return fromAddress.trim();
    }

    /** Set From kèm tên hiển thị "MovieZone"; lỗi encode tên hiển thị (thực tế không xảy ra với ASCII) chỉ log rồi dùng địa chỉ trần. */
    private void setBrandedFrom(MimeMessageHelper helper) throws MessagingException {
        try {
            helper.setFrom(requireFromAddress(), FROM_DISPLAY_NAME);
        } catch (UnsupportedEncodingException e) {
            log.warn("Không set được tên hiển thị người gửi, dùng địa chỉ trần: {}", e.getMessage());
            helper.setFrom(requireFromAddress());
        }
    }

    /**
     * Email dạng text thuần.
     */
    public void sendSimple(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(FROM_DISPLAY_NAME + " <" + requireFromAddress() + ">");
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    /**
     * Email HTML (UTF-8).
     */
    public void sendHtml(String to, String subject, String htmlBody) throws MessagingException {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
        setBrandedFrom(helper);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(mime);
    }

    /** Email HTML có ảnh nhúng (CID), dùng để gửi QR mà không lộ payload ra dịch vụ bên thứ ba. */
    public void sendHtmlWithInlineImages(String to, String subject, String htmlBody, Map<String, byte[]> images)
            throws MessagingException {
        MimeMessage mime = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
        setBrandedFrom(helper);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        for (Map.Entry<String, byte[]> image : images.entrySet()) {
            helper.addInline(image.getKey(), new org.springframework.core.io.ByteArrayResource(image.getValue()), "image/png");
        }
        mailSender.send(mime);
    }
}
