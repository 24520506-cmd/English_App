package com.englishapp.server.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
final class EmailOtpSender {
    private final JavaMailSender mail;
    private final String from;

    EmailOtpSender(JavaMailSender mail, @Value("${app.mail.from:}") String from) {
        this.mail = mail;
        this.from = from;
    }

    void send(String email, String code) {
        if (from.isBlank()) throw new IllegalStateException("Gmail delivery is not configured");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("English App - Mã xác thực email");
        message.setText("Mã xác thực English App của bạn là: " + code
                + "\n\nMã có hiệu lực trong 5 phút. Không chia sẻ mã này với người khác.");
        mail.send(message);
    }
}
