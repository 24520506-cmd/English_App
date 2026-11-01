package com.englishapp.server.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {
    private final JdbcTemplate db;
    private final OtpProof proof;
    private final EmailOtpSender sender;
    private final int ipHourLimit;

    EmailVerificationService(JdbcTemplate db, OtpProof proof, EmailOtpSender sender,
            @Value("${app.otp.ip-hour-limit:20}") int ipHourLimit) {
        this.db = db;
        this.proof = proof;
        this.sender = sender;
        this.ipHourLimit = ipHourLimit;
    }

    public Map<String, Object> create(UUID userId, String ip) {
        String email = currentEmail(userId);
        UUID challenge = UUID.randomUUID();
        String code = proof.newCode();
        Instant now = Instant.now();
        db.update("UPDATE otp_challenge SET invalidated_at=now() WHERE user_id=? "
                + "AND purpose='email_verification' AND used_at IS NULL AND invalidated_at IS NULL", userId);
        db.update("INSERT INTO otp_challenge(challenge_id,user_id,purpose,channel,contact_kind,contact_value,"
                + "verification_hash,issued_at,expires_at) VALUES (?,?,'email_verification','gmail','email',?,?,?,?)",
                challenge, userId, email, proof.hash(challenge.toString(), code),
                Timestamp.from(now), Timestamp.from(now.plusSeconds(300)));
        try {
            reserve(challenge, userId, email, ip);
        } catch (ApiFailure failure) {
            db.update("UPDATE otp_challenge SET invalidated_at=now() WHERE challenge_id=?", challenge);
            throw failure;
        }
        deliver(challenge, email, code);
        return Map.of("challenge_id", challenge.toString(), "masked_email", mask(email),
                "resend_after_at", now.plusSeconds(60).toString());
    }

    public Map<String, Object> resend(UUID userId, UUID challenge, String ip) {
        String email = currentEmail(userId);
        String code = proof.newCode();
        Boolean accepted = db.queryForObject("SELECT resend_user_otp(?,?,?,?,?)", Boolean.class,
                challenge, userId, proof.hash(challenge.toString(), code), ip, ipHourLimit);
        if (!Boolean.TRUE.equals(accepted)) throw limited();
        deliver(challenge, email, code);
        return Map.of("challenge_id", challenge.toString(), "masked_email", mask(email),
                "resend_after_at", Instant.now().plusSeconds(60).toString());
    }

    @Transactional
    public Map<String, Object> verify(UUID userId, UUID challenge, String code) {
        if (code == null || !code.matches("[0-9]{6}")) throw invalidOtp();
        String email = currentEmail(userId);
        Boolean complete = db.queryForObject("SELECT complete_email_verification(?,?,?,?)", Boolean.class,
                userId, challenge, email, proof.hash(challenge.toString(), code));
        if (!Boolean.TRUE.equals(complete)) throw invalidOtp();
        return Map.of("verified", true, "email", mask(email));
    }

    private void reserve(UUID challenge, UUID userId, String email, String ip) {
        Boolean accepted = db.queryForObject("SELECT reserve_otp_send(?,?,'email',?,'gmail',?::inet,?)",
                Boolean.class, challenge, userId, email, ip, ipHourLimit);
        if (!Boolean.TRUE.equals(accepted)) throw limited();
    }

    private void deliver(UUID challenge, String email, String code) {
        try {
            sender.send(email, code);
            db.update("UPDATE otp_send_attempt SET delivery_state='sent' WHERE challenge_id=? "
                    + "AND delivery_state='reserved'", challenge);
        } catch (RuntimeException failure) {
            db.update("UPDATE otp_send_attempt SET delivery_state='failed' WHERE challenge_id=? "
                    + "AND delivery_state='reserved'", challenge);
            db.update("UPDATE otp_challenge SET invalidated_at=now() WHERE challenge_id=?", challenge);
            throw new ApiFailure("OTP_DELIVERY_FAILED", HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể gửi mã qua Gmail. Vui lòng thử lại sau.");
        }
    }

    private String currentEmail(UUID userId) {
        String email = db.query("SELECT lower(btrim(email)) FROM app_user WHERE user_id=?",
                (rs, ignored) -> rs.getString(1), userId).stream().findFirst().orElse(null);
        if (email == null) throw new ApiFailure("EMAIL_REQUIRED", HttpStatus.BAD_REQUEST,
                "Tài khoản chưa có email.");
        return email;
    }

    private static String mask(String email) {
        int at = email.indexOf('@');
        return email.substring(0, 1) + "***" + email.substring(at);
    }

    private static ApiFailure limited() {
        return new ApiFailure("OTP_RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS,
                "Vui lòng chờ trước khi yêu cầu mã mới.");
    }

    private static ApiFailure invalidOtp() {
        return new ApiFailure("OTP_INVALID", HttpStatus.BAD_REQUEST,
                "Mã không đúng, đã hết hạn hoặc đã hết lượt thử.");
    }
}
