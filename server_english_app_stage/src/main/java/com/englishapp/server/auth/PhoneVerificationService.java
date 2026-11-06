package com.englishapp.server.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhoneVerificationService {
    private final JdbcTemplate db;
    private final OtpProof proof;
    private final SmsOtpSender sender;
    private final int ipHourLimit;

    PhoneVerificationService(JdbcTemplate db, OtpProof proof, SmsOtpSender sender,
            @Value("${app.otp.ip-hour-limit:20}") int ipHourLimit) {
        this.db = db;
        this.proof = proof;
        this.sender = sender;
        this.ipHourLimit = ipHourLimit;
    }

    public Map<String, Object> create(UUID userId, String ip) {
        String phone = currentPhone(userId);
        UUID challenge = UUID.randomUUID();
        String code = proof.newCode();
        Instant now = Instant.now();
        db.update("UPDATE otp_challenge SET invalidated_at = now() WHERE user_id = ? "
                + "AND purpose = 'phone_verification' AND used_at IS NULL AND invalidated_at IS NULL", userId);
        db.update("INSERT INTO otp_challenge(challenge_id,user_id,purpose,channel,contact_kind,contact_value,"
                + "verification_hash,issued_at,expires_at) VALUES (?,?,'phone_verification','sms','phone',?,?,?,?)",
                challenge, userId, phone, proof.hash(challenge.toString(), code),
                Timestamp.from(now), Timestamp.from(now.plusSeconds(300)));
        try {
            reserve(challenge, userId, phone, ip);
        } catch (ApiFailure limited) {
            db.update("UPDATE otp_challenge SET invalidated_at=now() WHERE challenge_id=?", challenge);
            throw limited;
        }
        deliver(challenge, code, phone);
        return response(challenge, phone, now.plusSeconds(60));
    }

    public Map<String, Object> resend(UUID userId, UUID challenge, String ip) {
        String phone = currentPhone(userId);
        String code = proof.newCode();
        Boolean accepted = db.queryForObject("SELECT resend_user_otp(?,?,?,?,?)", Boolean.class,
                challenge, userId, proof.hash(challenge.toString(), code), ip, ipHourLimit);
        if (!Boolean.TRUE.equals(accepted)) throw limited();
        deliver(challenge, code, phone);
        return response(challenge, phone, Instant.now().plusSeconds(60));
    }

    @Transactional
    public Map<String, Object> verify(UUID userId, UUID challenge, String code) {
        if (code == null || !code.matches("[0-9]{6}")) throw invalidOtp();
        String phone = currentPhone(userId);
        Boolean complete = db.queryForObject("SELECT complete_phone_verification(?,?,?,?)", Boolean.class,
                userId, challenge, phone, proof.hash(challenge.toString(), code));
        if (!Boolean.TRUE.equals(complete)) throw invalidOtp();
        return Map.of("verified", true, "phone", mask(phone));
    }

    private void reserve(UUID challenge, UUID userId, String phone, String ip) {
        Boolean accepted = db.queryForObject("SELECT reserve_otp_send(?,?,'phone',?,'sms',?::inet,?)",
                Boolean.class, challenge, userId, phone, ip, ipHourLimit);
        if (!Boolean.TRUE.equals(accepted)) throw limited();
    }

    private void deliver(UUID challenge, String code, String phone) {
        try {
            sender.send(phone, code, challenge.toString().replace("-", ""));
            db.update("UPDATE otp_send_attempt SET delivery_state='sent' WHERE challenge_id=? "
                    + "AND delivery_state='reserved'", challenge);
        } catch (RuntimeException failure) {
            db.update("UPDATE otp_send_attempt SET delivery_state='failed' WHERE challenge_id=? "
                    + "AND delivery_state='reserved'", challenge);
            db.update("UPDATE otp_challenge SET invalidated_at=now() WHERE challenge_id=?", challenge);
            throw new ApiFailure("OTP_DELIVERY_FAILED", HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể gửi mã qua SMS. Vui lòng thử lại sau.");
        }
    }

    private String currentPhone(UUID userId) {
        String phone = db.query("SELECT phone FROM app_user WHERE user_id=?",
                (rs, ignored) -> rs.getString(1), userId).stream().findFirst().orElse(null);
        if (phone == null) throw new ApiFailure("PHONE_REQUIRED", HttpStatus.BAD_REQUEST,
                "Tài khoản chưa có số điện thoại.");
        return phone;
    }

    private static Map<String, Object> response(UUID challenge, String phone, Instant resendAt) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("challenge_id", challenge.toString());
        result.put("masked_phone", mask(phone));
        result.put("resend_after_at", resendAt.toString());
        return result;
    }

    private static String mask(String phone) {
        return "*".repeat(Math.max(0, phone.length() - 3)) + phone.substring(Math.max(0, phone.length() - 3));
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
