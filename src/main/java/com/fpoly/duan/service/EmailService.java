package com.fpoly.duan.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

/**
 * Gửi email qua cấu hình {@code spring.mail.*} (Gmail SMTP trong application.properties).
 */
@Service
@RequiredArgsConstructor
public class EmailService {

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

    /**
     * Email dạng text thuần.
     */
    public void sendSimple(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(requireFromAddress());
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
        helper.setFrom(requireFromAddress());
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        mailSender.send(mime);
    }
}
