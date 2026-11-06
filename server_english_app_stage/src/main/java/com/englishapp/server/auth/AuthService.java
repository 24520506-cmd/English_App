package com.englishapp.server.auth;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,30}");
    private static final Set<String> CONTENT_BANDS = Set.of("BELOW_4_0", "4_0_5_0", "5_5_6_5", "7_0_PLUS");
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;

    public AuthService(JdbcTemplate db, PasswordEncoder passwords) {
        this.db = db;
        this.passwords = passwords;
    }

    public record User(UUID id, String username, String passwordHash, String role, String status,
                       String email, String phone, String displayName, Instant emailVerifiedAt,
                       Instant phoneVerifiedAt) { }
    private record Session(UUID id, UUID userId, String deviceLabel, String platform,
                           Instant created, Instant lastActivity, Instant absoluteExpiry,
                           Instant accessExpiry, Instant revoked) { }

    private User userByName(String username) {
        return db.query("SELECT user_id, username, password_hash, role, status, email, phone, display_name, "
                        + "email_verified_at, phone_verified_at FROM app_user WHERE username = ?",
                (rs, ignored) -> new User((UUID) rs.getObject("user_id"), rs.getString("username"),
                        rs.getString("password_hash"), rs.getString("role"), rs.getString("status"),
                        rs.getString("email"), rs.getString("phone"), rs.getString("display_name"),
                        instant(rs.getTimestamp("email_verified_at")), instant(rs.getTimestamp("phone_verified_at"))),
                username).stream().findFirst().orElse(null);
    }

    private User userById(UUID id) {
        return db.query("SELECT user_id, username, password_hash, role, status, email, phone, display_name, "
                        + "email_verified_at, phone_verified_at FROM app_user WHERE user_id = ?",
                (rs, ignored) -> new User((UUID) rs.getObject("user_id"), rs.getString("username"),
                        rs.getString("password_hash"), rs.getString("role"), rs.getString("status"),
                        rs.getString("email"), rs.getString("phone"), rs.getString("display_name"),
                        instant(rs.getTimestamp("email_verified_at")), instant(rs.getTimestamp("phone_verified_at"))),
                id).stream().findFirst().orElse(null);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private void lockUser(UUID userId) {
        db.queryForObject("SELECT user_id FROM app_user WHERE user_id = ? FOR UPDATE", UUID.class, userId);
    }

    private void loginEvent(UUID userId, UUID sessionId, String username, String ip, String outcome) {
        db.update("INSERT INTO security_event(user_id, session_id, event_type, outcome, "
                + "login_identifier, request_ip) VALUES (?, ?, 'login', ?, ?, ?::inet)",
                userId, sessionId, outcome, username, ip);
    }

    private Session sessionBy(String column, Object hash, boolean lock) {
        String sql = "SELECT session_id, user_id, device_label, platform, created_at, last_activity_at, "
                + "absolute_expires_at, access_expires_at, revoked_at FROM user_session WHERE " + column + " = ?"
                + (lock ? " FOR UPDATE" : "");
        return db.query(sql, (rs, ignored) -> new Session((UUID) rs.getObject("session_id"),
                (UUID) rs.getObject("user_id"), rs.getString("device_label"), rs.getString("platform"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("last_activity_at").toInstant(),
                rs.getTimestamp("absolute_expires_at").toInstant(), rs.getTimestamp("access_expires_at").toInstant(),
                rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant()),
                hash).stream().findFirst().orElse(null);
    }

    private static ApiFailure invalidSession(String code) {
        String message = code.equals("SESSION_REVOKED") ? "Phiên đã bị thu hồi. Vui lòng đăng nhập lại."
                : "Phiên không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.";
        return new ApiFailure(code, HttpStatus.UNAUTHORIZED, message);
    }

    private User checkSession(Session session, boolean checkAccessExpiry) {
        if (session == null) throw invalidSession("SESSION_INVALID");
        if (session.revoked() != null) throw invalidSession("SESSION_REVOKED");
        User user = userById(session.userId());
        if (user == null || !"active".equals(user.status())) throw invalidSession("SESSION_REVOKED");
        Duration idle = "admin".equals(user.role()) ? Duration.ofMinutes(30) : Duration.ofDays(7);
        Instant now = Instant.now();
        if (!now.isBefore(session.lastActivity().plus(idle)) || !now.isBefore(session.absoluteExpiry()))
            throw invalidSession("SESSION_EXPIRED");
        if (checkAccessExpiry && !now.isBefore(session.accessExpiry())) throw invalidSession("SESSION_INVALID");
        return user;
    }

    private static Map<String, Object> account(User user) {
        Map<String, Object> contacts = new LinkedHashMap<>();
        contacts.put("email", contact(user.email(), user.emailVerifiedAt()));
        contacts.put("phone", contact(user.phone(), user.phoneVerifiedAt()));
        contacts.put("pending_change", null);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("display_name", user.displayName());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user_id", user.id().toString());
        result.put("username", user.username());
        result.put("role", user.role());
        result.put("status", user.status());
        result.put("profile", profile);
        result.put("contacts", contacts);
        return result;
    }

    private static String mask(String value) {
        if (value.contains("@")) {
            String[] parts = value.split("@", 2);
            return parts[0].substring(0, 1) + "***@" + parts[1];
        }
        return "*".repeat(Math.max(0, value.length() - 3)) + value.substring(Math.max(0, value.length() - 3));
    }

    private static Map<String, Object> contact(String value, Instant verifiedAt) {
        if (value == null) return null;
        Map<String, Object> contact = new LinkedHashMap<>();
        contact.put("masked_value", mask(value));
        contact.put("verified_at", verifiedAt == null ? null : verifiedAt.toString());
        return contact;
    }

    @Transactional
    public Map<String, Object> register(String username, String password, String email, String phone) {
        if (username == null || !USERNAME.matcher(username).matches() || password == null || password.length() < 8)
            throw new ApiFailure("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "Thông tin đăng ký không hợp lệ.");
        if (email != null && (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length() > 320))
            throw new ApiFailure("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "Email không hợp lệ.");
        if (phone != null && !phone.matches("\\+?[0-9]{9,15}"))
            throw new ApiFailure("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "Số điện thoại không hợp lệ.");
        String normalized = username.toLowerCase(Locale.ROOT);
        if (userByName(normalized) != null)
            throw new ApiFailure("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "Tên đăng nhập đã được sử dụng.");
        UUID id = UUID.randomUUID();
        try {
            db.update("INSERT INTO app_user(user_id, username, password_hash, email, phone) VALUES (?, ?, ?, ?, ?)",
                    id, normalized, passwords.encode(password), email, phone);
        } catch (DuplicateKeyException duplicate) {
            throw new ApiFailure("VALIDATION_ERROR", HttpStatus.BAD_REQUEST, "Tên đăng nhập đã được sử dụng.");
        }
        return account(userById(id));
    }

    private void countLoginRequest(String username, String ip, Instant now) {
        db.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 3))", ip);
        db.update("DELETE FROM login_ip_request WHERE requested_at < ?", Timestamp.from(now.minus(Duration.ofMinutes(10))));
        Integer count = db.queryForObject("SELECT count(*) FROM login_ip_request WHERE ip_address = ? AND requested_at >= ?",
                Integer.class, ip, Timestamp.from(now.minus(Duration.ofMinutes(10))));
        db.update("INSERT INTO login_ip_request(ip_address, requested_at) VALUES (?, ?)", ip, Timestamp.from(now));
        if (count != null && count >= 20) throw ApiFailure.invalidCredentials();
        db.update("INSERT INTO login_failure(username, failures, window_started_at) VALUES (?, 0, ?) "
                + "ON CONFLICT (username) DO NOTHING", username, Timestamp.from(now));
    }

    private void recordFailure(String username, Instant now, int failures, Instant windowStart) {
        int next = failures + 1;
        db.update("UPDATE login_failure SET failures = ?, window_started_at = ?, blocked_until = ? WHERE username = ?",
                next, Timestamp.from(windowStart), next >= 5 ? Timestamp.from(now.plus(Duration.ofMinutes(15))) : null,
                username);
    }

    @Transactional(noRollbackFor = ApiFailure.class)
    public Map<String, Object> login(String username, String password, String deviceLabel, String platform, String ip) {
        if (username == null || !USERNAME.matcher(username).matches() || password == null
                || deviceLabel == null || platform == null)
            throw ApiFailure.invalidCredentials();
        String normalized = username.toLowerCase(Locale.ROOT);
        Instant now = Instant.now();
        try {
            countLoginRequest(normalized, ip, now);
        } catch (ApiFailure limited) {
            loginEvent(null, null, normalized, ip, "limited");
            throw limited;
        }
        Map<String, Object> limit = db.query("SELECT failures, window_started_at, blocked_until "
                + "FROM login_failure WHERE username = ? FOR UPDATE", (rs, ignored) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("failures", rs.getInt("failures"));
                    row.put("start", rs.getTimestamp("window_started_at").toInstant());
                    Timestamp blocked = rs.getTimestamp("blocked_until");
                    row.put("blocked", blocked == null ? null : blocked.toInstant());
                    return row;
                }, normalized).getFirst();
        Instant blocked = (Instant) limit.get("blocked");
        if (blocked != null && now.isBefore(blocked)) {
            loginEvent(null, null, normalized, ip, "blocked");
            throw ApiFailure.invalidCredentials();
        }
        User user = userByName(normalized);
        // Run a password check even for unknown accounts to reduce timing differences.
        String encoded = user == null ? "$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG" : user.passwordHash();
        boolean match = passwords.matches(password, encoded);
        if (user == null || !"active".equals(user.status()) || !match) {
            Instant start = (Instant) limit.get("start");
            boolean sameWindow = now.isBefore(start.plus(Duration.ofMinutes(15)));
            recordFailure(normalized, now, sameWindow ? (int) limit.get("failures") : 0,
                    sameWindow ? start : now);
            loginEvent(user == null ? null : user.id(), null, normalized, ip, "failure");
            throw ApiFailure.invalidCredentials();
        }
        lockUser(user.id());
        user = userById(user.id());
        if (user == null || !"active".equals(user.status()) || !passwords.matches(password, user.passwordHash())) {
            loginEvent(user == null ? null : user.id(), null, normalized, ip, "failure");
            throw ApiFailure.invalidCredentials();
        }
        db.update("DELETE FROM login_failure WHERE username = ?", normalized);
        UUID sessionId = UUID.randomUUID();
        String access = TokenCodec.newToken();
        String refresh = TokenCodec.newToken();
        Instant absolute = now.plus("admin".equals(user.role()) ? Duration.ofHours(8) : Duration.ofDays(30));
        db.update("INSERT INTO user_session(session_id, user_id, device_label, platform, created_at, last_activity_at, "
                + "absolute_expires_at, access_token_hash, access_expires_at, refresh_token_hash, "
                + "idle_timeout_seconds, login_ip) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::inet)", sessionId, user.id(), deviceLabel.substring(0, Math.min(80, deviceLabel.length())),
                platform.substring(0, Math.min(30, platform.length())), Timestamp.from(now), Timestamp.from(now),
                Timestamp.from(absolute), TokenCodec.sha256(access), Timestamp.from(now.plus(Duration.ofMinutes(15))),
                TokenCodec.sha256(refresh), "admin".equals(user.role()) ? 1800 : 604800, ip);
        loginEvent(user.id(), sessionId, normalized, ip, "success");
        return tokenResponse(sessionId, absolute, user, access, refresh, now.plus(Duration.ofMinutes(15)));
    }

    private static Map<String, Object> tokenResponse(UUID id, Instant absolute, User user,
                                                      String access, String refresh, Instant accessExpiry) {
        return Map.of("access_token", access, "access_expires_at", accessExpiry.toString(),
                "refresh_token", refresh,
                "session", Map.of("session_id", id.toString(), "absolute_expires_at", absolute.toString()),
                "user", Map.of("user_id", user.id().toString(), "role", user.role()));
    }

    @Transactional(noRollbackFor = TokenReuseFailure.class)
    public Map<String, Object> refresh(String oldRefresh) {
        if (oldRefresh == null || oldRefresh.isBlank()) throw invalidSession("SESSION_INVALID");
        String hash = TokenCodec.sha256(oldRefresh);
        Session observed = sessionBy("refresh_token_hash", hash, false);
        if (observed != null) lockUser(observed.userId());
        Session session = sessionBy("refresh_token_hash", hash, true);
        if (session == null) {
            var retired = db.query("SELECT r.session_id, s.user_id FROM retired_refresh_token r "
                    + "JOIN user_session s ON s.session_id = r.session_id WHERE r.token_hash = ?",
                    (rs, ignored) -> Map.entry((UUID) rs.getObject(1), (UUID) rs.getObject(2)), hash);
            if (!retired.isEmpty()) {
                lockUser(retired.getFirst().getValue());
                db.update("UPDATE user_session SET revoked_at = now(), revoke_reason = 'refresh_token_reuse' "
                        + "WHERE session_id = ? AND revoked_at IS NULL", retired.getFirst().getKey());
                db.update("INSERT INTO security_event(user_id, session_id, event_type, outcome) "
                                + "VALUES (?, ?, 'refresh_token_reuse', 'blocked')",
                        retired.getFirst().getValue(), retired.getFirst().getKey());
                throw new TokenReuseFailure();
            }
            throw invalidSession("SESSION_INVALID");
        }
        User user = checkSession(session, false);
        String access = TokenCodec.newToken();
        String refresh = TokenCodec.newToken();
        Instant accessExpiry = Instant.now().plus(Duration.ofMinutes(15));
        if (accessExpiry.isAfter(session.absoluteExpiry())) accessExpiry = session.absoluteExpiry();
        db.update("INSERT INTO retired_refresh_token(token_hash, session_id, replaced_by_hash) VALUES (?, ?, ?)",
                hash, session.id(), TokenCodec.sha256(refresh));
        db.update("UPDATE user_session SET access_token_hash = ?, access_expires_at = ?, refresh_token_hash = ? "
                + "WHERE session_id = ?", TokenCodec.sha256(access), Timestamp.from(accessExpiry),
                TokenCodec.sha256(refresh), session.id());
        return tokenResponse(session.id(), session.absoluteExpiry(), user, access, refresh, accessExpiry);
    }

    public User authenticate(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) throw invalidSession("SESSION_INVALID");
        return checkSession(sessionBy("access_token_hash", TokenCodec.sha256(accessToken), false), true);
    }

    public Map<String, Object> profile(UUID userId) {
        User user = userById(userId);
        if (user == null) throw invalidSession("SESSION_INVALID");
        return account(user);
    }

    @Transactional
    public Map<String, Object> updateProfile(UUID userId, Map<String, Object> changes) {
        if (changes == null || changes.size() != 1 || !changes.containsKey("display_name"))
            throw validationFailure();
        Object raw = changes.get("display_name");
        if (raw != null && !(raw instanceof String)) throw validationFailure();
        String displayName = raw == null ? null : ((String) raw).trim().replaceAll("\\s+", " ");
        if (displayName != null && (displayName.isEmpty() || displayName.length() > 80
                || displayName.chars().anyMatch(Character::isISOControl))) throw validationFailure();
        int updated = db.update("UPDATE app_user SET display_name = ? WHERE user_id = ?", displayName, userId);
        if (updated != 1) throw invalidSession("SESSION_INVALID");
        return profile(userId);
    }

    public Map<String, Object> preferences(UUID userId) {
        List<String> bands = db.query("SELECT band FROM learner_preferences WHERE user_id = ?",
                (rs, ignored) -> rs.getString("band"), userId);
        List<String> topicIds = db.query("SELECT topic_id FROM learner_preference_topic "
                        + "WHERE user_id = ? ORDER BY position",
                (rs, ignored) -> rs.getString("topic_id"), userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("band", bands.isEmpty() ? null : bands.getFirst());
        result.put("topic_ids", topicIds);
        return result;
    }

    @Transactional
    public Map<String, Object> updatePreferences(UUID userId, String band, List<String> topicIds) {
        if (band == null || !CONTENT_BANDS.contains(band) || topicIds == null || topicIds.isEmpty()
                || topicIds.size() > 8 || new HashSet<>(topicIds).size() != topicIds.size()
                || topicIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw validationFailure();
        }

        String placeholders = String.join(",", java.util.Collections.nCopies(topicIds.size(), "?"));
        List<String> knownTopics = db.query("SELECT topic_id FROM onboarding_topic WHERE topic_id IN ("
                        + placeholders + ")",
                (rs, ignored) -> rs.getString("topic_id"), topicIds.toArray());
        if (knownTopics.size() != topicIds.size()) throw validationFailure();

        db.update("INSERT INTO learner_preferences(user_id, band, updated_at) VALUES (?, ?, now()) "
                        + "ON CONFLICT (user_id) DO UPDATE SET band = EXCLUDED.band, updated_at = EXCLUDED.updated_at",
                userId, band);
        db.update("DELETE FROM learner_preference_topic WHERE user_id = ?", userId);
        for (int position = 0; position < topicIds.size(); position++) {
            db.update("INSERT INTO learner_preference_topic(user_id, topic_id, position) VALUES (?, ?, ?)",
                    userId, topicIds.get(position), position);
        }
        return preferences(userId);
    }

    private static ApiFailure validationFailure() {
        return new ApiFailure("VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                "Vui lòng kiểm tra thông tin đã nhập.");
    }

    @Transactional
    public void changePassword(String accessToken, String currentPassword, String newPassword) {
        Session beforeLock = sessionBy("access_token_hash", TokenCodec.sha256(accessToken), false);
        if (beforeLock == null) throw invalidSession("SESSION_INVALID");
        db.queryForObject("SELECT user_id FROM app_user WHERE user_id = ? FOR UPDATE",
                UUID.class, beforeLock.userId());
        Session current = sessionBy("access_token_hash", TokenCodec.sha256(accessToken), true);
        User user = checkSession(current, true);
        if (currentPassword == null || !passwords.matches(currentPassword, user.passwordHash()))
            throw new ApiFailure("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED,
                    "Thông tin đăng nhập không hợp lệ");
        if (newPassword == null || newPassword.length() < 8) throw validationFailure();
        db.update("UPDATE app_user SET password_hash = ? WHERE user_id = ?",
                passwords.encode(newPassword), user.id());
        db.update("UPDATE user_session SET revoked_at = now(), revoke_reason = 'password_change' "
                + "WHERE user_id = ? AND revoked_at IS NULL", user.id());
        db.update("INSERT INTO security_event(user_id, session_id, event_type, outcome) "
                + "VALUES (?, ?, 'password_change', 'success')", user.id(), current.id());
    }

    public Map<String, Object> sessions(String accessToken) {
        Session current = sessionBy("access_token_hash", TokenCodec.sha256(accessToken), false);
        User user = checkSession(current, true);
        Duration idle = "admin".equals(user.role()) ? Duration.ofMinutes(30) : Duration.ofDays(7);
        List<Map<String, Object>> rows = db.query("SELECT session_id, device_label, platform, created_at, "
                        + "last_activity_at, absolute_expires_at FROM user_session WHERE user_id = ? "
                        + "AND revoked_at IS NULL AND now() < absolute_expires_at "
                        + "AND now() < last_activity_at + (? * interval '1 second') ORDER BY created_at DESC",
                (rs, ignored) -> Map.<String, Object>of(
                        "session_id", rs.getObject("session_id").toString(),
                        "device_label", rs.getString("device_label"),
                        "platform", rs.getString("platform"),
                        "created_at", rs.getTimestamp("created_at").toInstant().toString(),
                        "last_activity_at", rs.getTimestamp("last_activity_at").toInstant().toString(),
                        "idle_expires_at", rs.getTimestamp("last_activity_at").toInstant().plus(idle).toString(),
                        "absolute_expires_at", rs.getTimestamp("absolute_expires_at").toInstant().toString(),
                        "is_current", current.id().equals(rs.getObject("session_id"))),
                user.id(), idle.toSeconds());
        return Map.of("sessions", rows);
    }

    @Transactional
    public void revokeSession(String accessToken, String sessionId) {
        Session current = sessionBy("access_token_hash", TokenCodec.sha256(accessToken), false);
        if (current == null) throw invalidSession("SESSION_INVALID");
        lockUser(current.userId());
        current = sessionBy("session_id", current.id(), true);
        User user = checkSession(current, true);
        UUID target;
        try { target = UUID.fromString(sessionId); }
        catch (IllegalArgumentException badId) { throw notFound(); }
        int changed = db.update("UPDATE user_session SET revoked_at = now(), revoke_reason = 'user_revoke' "
                + "WHERE session_id = ? AND user_id = ? AND revoked_at IS NULL "
                + "AND now() < absolute_expires_at AND now() < last_activity_at + (? * interval '1 second')",
                target, user.id(), "admin".equals(user.role()) ? 1800 : 604800);
        if (changed == 0) throw notFound();
        db.update("INSERT INTO security_event(user_id, session_id, event_type, outcome) "
                + "VALUES (?, ?, 'session_revoke', 'success')", user.id(), target);
    }

    private static ApiFailure notFound() {
        return new ApiFailure("NOT_FOUND", HttpStatus.NOT_FOUND, "Không tìm thấy phiên.");
    }

    @Transactional
    public void revokeAllSessions(String accessToken) {
        Session current = sessionBy("access_token_hash", TokenCodec.sha256(accessToken), false);
        if (current == null) throw invalidSession("SESSION_INVALID");
        lockUser(current.userId());
        current = sessionBy("session_id", current.id(), true);
        User user = checkSession(current, true);
        db.update("UPDATE user_session SET revoked_at = now(), revoke_reason = 'user_revoke_all' "
                + "WHERE user_id = ? AND revoked_at IS NULL", user.id());
        db.update("INSERT INTO security_event(user_id, session_id, event_type, outcome) "
                + "VALUES (?, ?, 'session_revoke_all', 'success')", user.id(), current.id());
    }

    public Map<String, Object> session(String accessToken) {
        Session session = sessionBy("access_token_hash", TokenCodec.sha256(accessToken), false);
        User user = checkSession(session, true);
        Duration idle = "admin".equals(user.role()) ? Duration.ofMinutes(30) : Duration.ofDays(7);
        return Map.of("user", account(user), "session", Map.of(
                "session_id", session.id().toString(), "device_label", session.deviceLabel(),
                "platform", session.platform(), "created_at", session.created().toString(),
                "last_activity_at", session.lastActivity().toString(),
                "idle_expires_at", session.lastActivity().plus(idle).toString(),
                "absolute_expires_at", session.absoluteExpiry().toString(), "is_current", true));
    }

    @Transactional
    public void logout(String accessToken) {
        Session observed = sessionBy("access_token_hash", TokenCodec.sha256(accessToken), false);
        if (observed == null) throw invalidSession("SESSION_INVALID");
        lockUser(observed.userId());
        Session session = sessionBy("session_id", observed.id(), true);
        checkSession(session, true);
        db.update("UPDATE user_session SET revoked_at = now(), revoke_reason = 'logout' WHERE session_id = ?", session.id());
        db.update("INSERT INTO security_event(user_id, session_id, event_type, outcome) "
                + "VALUES (?, ?, 'logout', 'success')", session.userId(), session.id());
    }

    @Transactional
    public void activity(String accessToken) {
        Session session = sessionBy("access_token_hash", TokenCodec.sha256(accessToken), true);
        checkSession(session, true);
        db.update("UPDATE user_session SET last_activity_at = now() WHERE session_id = ?", session.id());
    }
}
