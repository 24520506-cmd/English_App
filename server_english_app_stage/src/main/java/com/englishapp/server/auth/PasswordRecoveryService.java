package com.englishapp.server.auth;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PasswordRecoveryService {
    private static final String GENERIC_MESSAGE =
            "Nếu thông tin phù hợp, mã khôi phục sẽ được gửi qua kênh đã chọn";

    private final JdbcTemplate db;
    private final OtpProof proof;
    private final PasswordEncoder passwords;
    private final EmailOtpSender emailSender;
    private final SmsOtpSender smsSender;
    private final int ipHourLimit;

    PasswordRecoveryService(JdbcTemplate db, OtpProof proof, PasswordEncoder passwords,
            EmailOtpSender emailSender, SmsOtpSender smsSender,
            @Value("${app.otp.ip-hour-limit:20}") int ipHourLimit) {
        this.db = db;
        this.proof = proof;
        this.passwords = passwords;
        this.emailSender = emailSender;
        this.smsSender = smsSender;
        this.ipHourLimit = ipHourLimit;
    }

    @Transactional
    Map<String, Object> start(String channel, String loginIdentifier, String rawContact, String ip) {
        UUID challengeId = UUID.randomUUID();
        String normalizedChannel = channel == null ? "" : channel.trim().toLowerCase(Locale.ROOT);
        String kind;
        String storedChannel;
        String contact;
        if ("gmail".equals(normalizedChannel)) {
            kind = "email";
            storedChannel = "gmail";
            contact = rawContact == null ? "" : rawContact.trim().toLowerCase();
            if (!contact.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || contact.length() > 320) {
                return generic(challengeId);
            }
        } else if ("zalo".equals(normalizedChannel)) {
            kind = "phone";
            storedChannel = "sms";
            contact = AuthService.normalizePhone(rawContact);
            if (contact == null || !contact.matches("\\+[1-9][0-9]{8,14}")) return generic(challengeId);
        } else {
            return generic(challengeId);
        }

        if (loginIdentifier == null || !loginIdentifier.matches("[A-Za-z0-9_]{3,30}"))
            return generic(challengeId);
        String username = loginIdentifier.toLowerCase(Locale.ROOT);
        UUID userId = db.query("SELECT user_id FROM app_user WHERE lower(username)=? AND "
                        + ("email".equals(kind) ? "lower(btrim(email))=?" : "phone=?"),
                (rs, ignored) -> (UUID) rs.getObject(1), username, contact).stream().findFirst().orElse(null);
        if (userId == null) return generic(challengeId);

        db.update("UPDATE otp_challenge SET invalidated_at=now() WHERE user_id=? "
                + "AND purpose='password_reset' AND used_at IS NULL AND invalidated_at IS NULL", userId);
        db.update("UPDATE password_reset_grant SET invalidated_at=now() WHERE user_id=? "
                + "AND consumed_at IS NULL AND invalidated_at IS NULL", userId);
        String code = proof.newCode();
        Instant now = Instant.now();
        db.update("INSERT INTO otp_challenge(challenge_id,user_id,purpose,channel,contact_kind,contact_value,"
                        + "verification_hash,issued_at,expires_at) VALUES (?,?,'password_reset',?,?,?,?,?,?)",
                challengeId, userId, storedChannel, kind, contact,
                proof.hash(challengeId.toString(), code), Timestamp.from(now), Timestamp.from(now.plusSeconds(300)));
        Boolean reserved = db.queryForObject("SELECT reserve_otp_send(?,?,?,?,?,?::inet,?)", Boolean.class,
                challengeId, userId, kind, contact, storedChannel, ip, ipHourLimit);
        if (Boolean.TRUE.equals(reserved)) deliver(challengeId, storedChannel, contact, code);
        else invalidate(challengeId);
        return generic(challengeId);
    }

    @Transactional
    Map<String, Object> resend(UUID challengeId, String ip) {
        var challenge = db.query("SELECT user_id, channel, contact_kind, contact_value FROM otp_challenge "
                        + "WHERE challenge_id=? AND purpose='password_reset'",
                (rs, ignored) -> new RecoveryChallenge((UUID) rs.getObject("user_id"),
                        rs.getString("channel"), rs.getString("contact_kind"), rs.getString("contact_value")),
                challengeId).stream().findFirst().orElse(null);
        if (challenge != null) {
            String code = proof.newCode();
            Boolean accepted = db.queryForObject("SELECT resend_user_otp(?,?,?,?::inet,?)", Boolean.class,
                    challengeId, challenge.userId(), proof.hash(challengeId.toString(), code), ip, ipHourLimit);
            if (Boolean.TRUE.equals(accepted)) deliver(challengeId, challenge.channel(), challenge.contact(), code);
        }
        return generic(challengeId == null ? UUID.randomUUID() : challengeId);
    }

    @Transactional(noRollbackFor = ApiFailure.class)
    Map<String, Object> verify(UUID challengeId, String otp) {
        if (challengeId == null || otp == null || !otp.matches("[0-9]{6}")) throw invalidOtp();
        var challenge = db.query("SELECT user_id, channel, contact_kind, contact_value FROM otp_challenge "
                        + "WHERE challenge_id=? AND purpose='password_reset'",
                (rs, ignored) -> new RecoveryChallenge((UUID) rs.getObject("user_id"),
                        rs.getString("channel"), rs.getString("contact_kind"), rs.getString("contact_value")),
                challengeId).stream().findFirst().orElse(null);
        if (challenge == null) throw invalidOtp();
        String result = db.queryForObject("SELECT consume_user_otp(?,?,?,?,?,?,?)", String.class,
                challenge.userId(), challengeId, "password_reset", challenge.kind(), challenge.contact(),
                challenge.channel(), proof.hash(challengeId.toString(), otp));
        if (!"consumed".equals(result)) throw invalidOtp();

        String grant = TokenCodec.newToken();
        UUID grantId = UUID.randomUUID();
        Timestamp expires = db.queryForObject("INSERT INTO password_reset_grant(grant_id,user_id,challenge_id,"
                        + "grant_hash,issued_at,expires_at) VALUES (?,?,?,?,now(),now()+interval '5 minutes') "
                        + "RETURNING expires_at", (rs, ignored) -> rs.getTimestamp(1),
                grantId, challenge.userId(), challengeId, TokenCodec.sha256(grant));
        return Map.of("reset_grant", grant, "expires_at", expires.toInstant().toString());
    }

    @Transactional
    void complete(String grant, String newPassword) {
        if (grant == null || grant.isBlank() || newPassword == null || newPassword.length() < 8)
            throw validationFailure();
        Boolean completed = db.queryForObject("SELECT complete_password_reset(?,?)", Boolean.class,
                TokenCodec.sha256(grant), passwords.encode(newPassword));
        if (!Boolean.TRUE.equals(completed))
            throw new ApiFailure("RESET_GRANT_INVALID", HttpStatus.BAD_REQUEST,
                    "Yêu cầu đặt lại mật khẩu không hợp lệ hoặc đã hết hạn.");
    }

    private void deliver(UUID challengeId, String channel, String contact, String code) {
        try {
            if ("gmail".equals(channel)) emailSender.send(contact, code);
            else smsSender.send(contact, code, challengeId.toString().replace("-", ""));
            db.update("UPDATE otp_send_attempt SET delivery_state='sent' WHERE send_id=(SELECT send_id "
                    + "FROM otp_send_attempt WHERE challenge_id=? AND delivery_state='reserved' "
                    + "ORDER BY attempted_at DESC LIMIT 1)", challengeId);
        } catch (RuntimeException failure) {
            db.update("UPDATE otp_send_attempt SET delivery_state='failed' WHERE challenge_id=? "
                    + "AND delivery_state='reserved'", challengeId);
            invalidate(challengeId);
        }
    }

    private void invalidate(UUID challengeId) {
        db.update("UPDATE otp_challenge SET invalidated_at=now() WHERE challenge_id=? "
                + "AND used_at IS NULL AND invalidated_at IS NULL", challengeId);
    }

    private static Map<String, Object> generic(UUID challengeId) {
        return Map.of("message", GENERIC_MESSAGE, "challenge_id", challengeId.toString());
    }

    private static ApiFailure invalidOtp() {
        return new ApiFailure("OTP_INVALID", HttpStatus.BAD_REQUEST,
                "Mã không đúng, đã hết hạn hoặc đã hết lượt thử.");
    }

    private static ApiFailure validationFailure() {
        return new ApiFailure("VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                "Vui lòng kiểm tra thông tin đã nhập.");
    }

    private record RecoveryChallenge(UUID userId, String channel, String kind, String contact) { }
}
