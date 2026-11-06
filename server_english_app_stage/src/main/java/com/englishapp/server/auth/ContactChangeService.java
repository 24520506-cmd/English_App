package com.englishapp.server.auth;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
final class ContactChangeService {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final OtpProof proof;
    private final SmsOtpSender smsSender;
    private final AuthService auth;
    private final int ipHourLimit;

    ContactChangeService(JdbcTemplate db, PasswordEncoder passwords, OtpProof proof,
            SmsOtpSender smsSender, AuthService auth,
            @Value("${app.otp.ip-hour-limit:20}") int ipHourLimit) {
        this.db = db;
        this.passwords = passwords;
        this.proof = proof;
        this.smsSender = smsSender;
        this.auth = auth;
        this.ipHourLimit = ipHourLimit;
    }

    Map<String, Object> start(String accessToken, String kind, String rawValue,
            String currentPassword, String ip) {
        if (accessToken == null || accessToken.isBlank() || currentPassword == null
                || kind == null || rawValue == null) throw validationFailure();

        var session = db.query("SELECT s.session_id,s.user_id,s.last_activity_at,s.absolute_expires_at,"
                        + "s.access_expires_at,s.revoked_at,s.idle_timeout_seconds,u.password_hash "
                        + "FROM user_session s JOIN app_user u ON u.user_id=s.user_id "
                        + "WHERE s.access_token_hash=? AND u.status='active'",
                (rs, ignored) -> new ContactSession((UUID) rs.getObject("session_id"),
                        (UUID) rs.getObject("user_id"), rs.getTimestamp("last_activity_at").toInstant(),
                        rs.getTimestamp("absolute_expires_at").toInstant(),
                        rs.getTimestamp("access_expires_at").toInstant(),
                        rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
                        rs.getInt("idle_timeout_seconds"), rs.getString("password_hash")),
                TokenCodec.sha256(accessToken)).stream().findFirst().orElse(null);
        if (session == null || !session.isLive()) throw invalidSession();
        db.queryForObject("SELECT user_id FROM app_user WHERE user_id=? FOR UPDATE", UUID.class, session.userId());
        if (!passwords.matches(currentPassword, session.passwordHash())) throw ApiFailure.invalidCredentials();

        String contactKind = kind.trim().toLowerCase(Locale.ROOT);
        String channel;
        String candidate;
        if ("email".equals(contactKind)) {
            channel = "gmail";
            candidate = rawValue.trim().toLowerCase(Locale.ROOT);
            if (candidate.length() > 320 || !candidate.matches("[^\\s@]+@gmail\\.com"))
                throw validationFailure();
        } else if ("phone".equals(contactKind)) {
            channel = "sms";
            candidate = AuthService.normalizePhone(rawValue);
            if (candidate == null || !candidate.matches("\\+[1-9][0-9]{8,14}")) throw validationFailure();
        } else {
            throw validationFailure();
        }

        UUID proofId = UUID.randomUUID();
        String proofToken = TokenCodec.newToken();
        db.update("INSERT INTO reauth_proof(proof_id,user_id,session_id,operation,proof_hash,issued_at,expires_at) "
                        + "VALUES (?,?,?,'contact_change',?,now(),now()+interval '5 minutes')",
                proofId, session.userId(), session.sessionId(), TokenCodec.sha256(proofToken));
        UUID changeId = db.queryForObject("SELECT start_contact_change(?,?,?,?,?,?)", UUID.class,
                session.userId(), session.sessionId(), TokenCodec.sha256(proofToken), contactKind, candidate, channel);
        if (changeId == null) throw new ApiFailure("CONTACT_ALREADY_LINKED", HttpStatus.CONFLICT,
                "Thông tin liên hệ không thể sử dụng cho tài khoản này.");

        if ("email".equals(contactKind)) {
            return Map.of("change_id", changeId.toString(), "kind", contactKind,
                    "masked_value", mask(candidate), "verification_method", "google_oauth",
                    "expires_at", java.time.Instant.now().plusSeconds(600).toString());
        }

        UUID challengeId = UUID.randomUUID();
        String otp = proof.newCode();
        db.update("INSERT INTO otp_challenge(challenge_id,user_id,change_id,purpose,channel,contact_kind,"
                        + "contact_value,verification_hash,issued_at,expires_at) "
                        + "VALUES (?,?,?,'contact_change',?,?,?,?,now(),now()+interval '5 minutes')",
                challengeId, session.userId(), changeId, channel, contactKind, candidate,
                proof.hash(challengeId.toString(), otp));
        Boolean reserved = db.queryForObject("SELECT reserve_otp_send(?,?,?,?,?,?::inet,?)", Boolean.class,
                challengeId, session.userId(), contactKind, candidate, channel, ip, ipHourLimit);
        if (!Boolean.TRUE.equals(reserved)) {
            db.update("UPDATE otp_challenge SET invalidated_at=now() WHERE challenge_id=?", challengeId);
            db.update("UPDATE pending_contact_change SET invalidated_at=now() WHERE change_id=?", changeId);
            throw rateLimited();
        }
        deliver(changeId, challengeId, channel, candidate, otp);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("change_id", changeId.toString());
        result.put("challenge_id", challengeId.toString());
        result.put("kind", contactKind);
        result.put("masked_value", mask(candidate));
        result.put("resend_after_at", java.time.Instant.now().plusSeconds(60).toString());
        return result;
    }

    @Transactional(noRollbackFor = ApiFailure.class)
    Map<String, Object> verify(UUID userId, UUID changeId, String otp) {
        if (changeId == null || otp == null || !otp.matches("[0-9]{6}")) throw invalidOtp();
        boolean emailChange = db.queryForObject("SELECT EXISTS(SELECT 1 FROM pending_contact_change "
                        + "WHERE change_id=? AND user_id=? AND contact_kind='email' "
                        + "AND completed_at IS NULL AND invalidated_at IS NULL AND expires_at>now())",
                Boolean.class, changeId, userId);
        if (Boolean.TRUE.equals(emailChange))
            throw new ApiFailure("VERIFICATION_METHOD_REQUIRED", HttpStatus.BAD_REQUEST,
                    "Hãy xác minh Gmail bằng Google OAuth.");
        var challenge = db.query("SELECT challenge_id,contact_kind,contact_value,channel FROM otp_challenge "
                        + "WHERE change_id=? AND user_id=? AND purpose='contact_change' "
                        + "AND used_at IS NULL AND invalidated_at IS NULL ORDER BY issued_at DESC LIMIT 1",
                (rs, ignored) -> new ContactChallenge((UUID) rs.getObject("challenge_id"),
                        rs.getString("contact_kind"), rs.getString("contact_value"), rs.getString("channel")),
                changeId, userId).stream().findFirst().orElse(null);
        if (challenge == null) throw invalidOtp();
        Boolean completed = db.queryForObject("SELECT complete_contact_change(?,?,?)", Boolean.class,
                changeId, challenge.id(), proof.hash(challenge.id().toString(), otp));
        if (!Boolean.TRUE.equals(completed)) throw invalidOtp();
        return auth.profile(userId);
    }

    private void deliver(UUID changeId, UUID challengeId, String channel, String contact, String code) {
        try {
            smsSender.send(contact, code, challengeId.toString().replace("-", ""));
            db.update("UPDATE otp_send_attempt SET delivery_state='sent' WHERE send_id=(SELECT send_id "
                    + "FROM otp_send_attempt WHERE challenge_id=? AND delivery_state='reserved' "
                    + "ORDER BY attempted_at DESC LIMIT 1)", challengeId);
        } catch (RuntimeException failure) {
            db.update("UPDATE otp_send_attempt SET delivery_state='failed' WHERE challenge_id=? "
                    + "AND delivery_state='reserved'", challengeId);
            db.update("UPDATE otp_challenge SET invalidated_at=now() WHERE challenge_id=?", challengeId);
            db.update("UPDATE pending_contact_change SET invalidated_at=now() WHERE change_id=?", changeId);
            throw new ApiFailure("OTP_DELIVERY_FAILED", HttpStatus.SERVICE_UNAVAILABLE,
                    "Không thể gửi mã xác thực. Vui lòng thử lại sau.");
        }
    }

    private static String mask(String value) {
        if (value.contains("@")) {
            int at = value.indexOf('@');
            return value.substring(0, 1) + "***" + value.substring(at);
        }
        return "*".repeat(Math.max(0, value.length() - 3)) + value.substring(Math.max(0, value.length() - 3));
    }

    private static ApiFailure invalidSession() {
        return new ApiFailure("SESSION_INVALID", HttpStatus.UNAUTHORIZED,
                "Phiên không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.");
    }

    private static ApiFailure validationFailure() {
        return new ApiFailure("VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                "Vui lòng kiểm tra thông tin đã nhập.");
    }

    private static ApiFailure invalidOtp() {
        return new ApiFailure("OTP_INVALID", HttpStatus.BAD_REQUEST,
                "Mã không đúng, đã hết hạn hoặc đã hết lượt thử.");
    }

    private static ApiFailure rateLimited() {
        return new ApiFailure("OTP_RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS,
                "Vui lòng chờ trước khi yêu cầu mã mới.");
    }

    private record ContactChallenge(UUID id, String kind, String value, String channel) { }

    private record ContactSession(UUID sessionId, UUID userId, java.time.Instant lastActivity,
            java.time.Instant absoluteExpiry, java.time.Instant accessExpiry, java.time.Instant revokedAt,
            int idleTimeoutSeconds, String passwordHash) {
        boolean isLive() {
            java.time.Instant now = java.time.Instant.now();
            return revokedAt == null && now.isBefore(accessExpiry) && now.isBefore(absoluteExpiry)
                    && now.isBefore(lastActivity.plusSeconds(idleTimeoutSeconds));
        }
    }
}
