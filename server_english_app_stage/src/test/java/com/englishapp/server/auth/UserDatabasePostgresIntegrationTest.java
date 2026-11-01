package com.englishapp.server.auth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@SpringBootTest
class UserDatabasePostgresIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "english_app"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", ""));
    }

    @Autowired JdbcTemplate db;
    @Autowired DataSource dataSource;

    private static String hash() { return UUID.randomUUID().toString().replace("-", "").repeat(2); }

    private UUID user(String email) {
        UUID id = UUID.randomUUID();
        String username = "u" + id.toString().replace("-", "").substring(0, 20);
        db.update("INSERT INTO app_user(user_id, username, password_hash, email) VALUES (?, ?, ?, ?)",
                id, username, "old-hash", email);
        return id;
    }

    private UUID session(UUID userId) {
        UUID id = UUID.randomUUID();
        db.update("INSERT INTO user_session(session_id, user_id, device_label, platform, created_at, "
                        + "last_activity_at, absolute_expires_at, access_token_hash, access_expires_at, "
                        + "refresh_token_hash, idle_timeout_seconds) "
                        + "VALUES (?, ?, 'test', 'android', now(), now(), now() + interval '30 days', ?, "
                        + "now() + interval '15 minutes', ?, 604800)", id, userId, hash(), hash());
        return id;
    }

    private <T> List<T> race(java.util.concurrent.Callable<T> first, java.util.concurrent.Callable<T> second)
            throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<T> a = pool.submit(() -> { start.await(); return first.call(); });
            Future<T> b = pool.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            return List.of(a.get(), b.get());
        }
    }

    private void awaitDatabaseLock(int pid) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            String waitType = db.queryForObject("SELECT wait_event_type FROM pg_stat_activity WHERE pid = ?",
                    String.class, pid);
            if ("Lock".equals(waitType)) return;
            Thread.sleep(20);
        }
        fail("The competing database call never reached its row lock");
    }

    private int backendPid(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_backend_pid()");
                ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getInt(1);
        }
    }

    @Test void legacyUpgradeAndConstraints() {
        String schema = "userdb_test_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("TEST_DATABASE_URL");
        String user = System.getenv().getOrDefault("TEST_DATABASE_USER", "english_app");
        String password = System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", "");
        Flyway v3 = Flyway.configure().dataSource(url, user, password).schemas(schema)
                .defaultSchema(schema).createSchemas(true).locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("3")).load();
        assertEquals(3, v3.migrate().migrationsExecuted);
        UUID legacyId = UUID.randomUUID();
        db.update("INSERT INTO " + schema + ".app_user(user_id, username, password_hash, email, "
                + "email_verified_at) VALUES (?, 'legacy_user', 'legacy-hash', 'legacy@example.com', now())",
                legacyId);
        UUID legacySession = UUID.randomUUID();
        db.update("INSERT INTO " + schema + ".user_session(session_id, user_id, device_label, platform, "
                        + "created_at, last_activity_at, absolute_expires_at, access_token_hash, "
                        + "access_expires_at, refresh_token_hash) VALUES (?, ?, 'legacy', 'android', "
                        + "now() - interval '30 days', now() - interval '1 day', now() + interval '1 minute', "
                        + "?, now() + interval '15 minutes', ?)", legacySession, legacyId, hash(), hash());
        Flyway latest = Flyway.configure().dataSource(url, user, password).schemas(schema)
                .defaultSchema(schema).locations("classpath:db/migration").load();
        assertTrue(latest.migrate().migrationsExecuted >= 3);
        assertEquals(legacyId, db.queryForObject("SELECT user_id FROM " + schema
                + ".app_user WHERE username = 'legacy_user'", UUID.class));
        assertEquals(legacyId, db.queryForObject("SELECT user_id FROM " + schema
                + ".verified_contact_claim WHERE contact_kind = 'email' "
                + "AND normalized_value = 'legacy@example.com'", UUID.class));
        assertEquals(Boolean.TRUE, db.queryForObject("SELECT access_expires_at = absolute_expires_at "
                + "FROM " + schema + ".user_session WHERE session_id = ?", Boolean.class, legacySession));

        String fresh = "userdb_test_" + UUID.randomUUID().toString().replace("-", "");
        Flyway emptyToLatest = Flyway.configure().dataSource(url, user, password).schemas(fresh)
                .defaultSchema(fresh).createSchemas(true).locations("classpath:db/migration").load();
        assertTrue(emptyToLatest.migrate().migrationsExecuted >= 6);

        String shared = "same" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        UUID first = user(shared);
        UUID second = user(shared);
        db.update("UPDATE app_user SET email_verified_at = now() WHERE user_id = ?", first);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> db.update("UPDATE app_user SET email_verified_at = now() WHERE user_id = ?", second));
        assertEquals(first, db.queryForObject("SELECT user_id FROM app_user WHERE email_verified_at IS NOT NULL "
                + "AND email = ?", UUID.class, shared));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> db.update("INSERT INTO google_identity(google_sub, user_id, verified_email) "
                        + "VALUES (?, ?, ?)", "google-" + UUID.randomUUID(), second, shared));
        assertEquals(first, db.queryForObject("SELECT user_id FROM verified_contact_claim "
                + "WHERE contact_kind = 'email' AND normalized_value = ?", UUID.class, shared));
    }

    @Test void concurrentOtpConsume() throws Exception {
        UUID userId = user("otp@example.com");
        UUID challenge = UUID.randomUUID();
        String proof = hash();
        db.update("INSERT INTO otp_challenge(challenge_id, user_id, purpose, channel, contact_kind, "
                        + "contact_value, verification_hash, issued_at, expires_at) VALUES (?, ?, "
                        + "'password_reset', 'gmail', 'email', 'otp@example.com', ?, now(), "
                        + "now() + interval '5 minutes')", challenge, userId, proof);
        List<String> results = race(
                () -> db.queryForObject("SELECT consume_user_otp(?, ?, 'password_reset', 'email', "
                        + "'otp@example.com', 'gmail', ?)", String.class, userId, challenge, proof),
                () -> db.queryForObject("SELECT consume_user_otp(?, ?, 'password_reset', 'email', "
                        + "'otp@example.com', 'gmail', ?)", String.class, userId, challenge, proof));
        assertEquals(1, results.stream().filter("consumed"::equals).count());
        assertEquals(1, results.stream().filter("used"::equals).count());
        assertEquals(1, db.queryForObject("SELECT count(*) FROM otp_challenge WHERE challenge_id = ? "
                + "AND used_at IS NOT NULL", Integer.class, challenge));
    }

    @Test void otpPurposeAndFifthWrongAttemptAreEnforced() {
        UUID userId = user("purpose@example.com");
        UUID challenge = UUID.randomUUID();
        String correctHash = hash();
        db.update("INSERT INTO otp_challenge(challenge_id, user_id, purpose, channel, contact_kind, "
                        + "contact_value, verification_hash, issued_at, expires_at) VALUES (?, ?, "
                        + "'password_reset', 'gmail', 'email', 'purpose@example.com', ?, now(), "
                        + "now() + interval '5 minutes')", challenge, userId, correctHash);
        assertEquals("invalid", db.queryForObject("SELECT consume_user_otp(?, ?, 'phone_verification', "
                + "'email', 'purpose@example.com', 'gmail', ?)", String.class, userId, challenge, correctHash));
        assertEquals(0, db.queryForObject("SELECT wrong_attempts FROM otp_challenge WHERE challenge_id = ?",
                Integer.class, challenge));
        for (int i = 1; i <= 5; i++) {
            String result = db.queryForObject("SELECT consume_user_otp(?, ?, 'password_reset', 'email', "
                    + "'purpose@example.com', 'gmail', ?)", String.class, userId, challenge, hash());
            assertEquals(i == 5 ? "attempts_exhausted" : "invalid", result);
        }
        assertNotEquals("consumed", db.queryForObject("SELECT consume_user_otp(?, ?, 'password_reset', "
                + "'email', 'purpose@example.com', 'gmail', ?)", String.class, userId, challenge, correctHash));
        assertEquals(5, db.queryForObject("SELECT wrong_attempts FROM otp_challenge WHERE challenge_id = ?",
                Integer.class, challenge));
    }

    @Test void concurrentOtpSendRespectsHourlyLimit() throws Exception {
        UUID userId = user("limit@example.com");
        UUID challenge = UUID.randomUUID();
        db.update("INSERT INTO otp_challenge(challenge_id, user_id, purpose, channel, contact_kind, "
                        + "contact_value, verification_hash, issued_at, expires_at) VALUES (?, ?, "
                        + "'password_reset', 'gmail', 'email', 'limit@example.com', ?, now(), "
                        + "now() + interval '5 minutes')", challenge, userId, hash());
        for (int i = 0; i < 4; i++) {
            db.update("INSERT INTO otp_send_attempt(send_id, challenge_id, user_id, contact_kind, "
                            + "contact_value, channel, request_ip, attempted_at, delivery_state) "
                            + "VALUES (?, ?, ?, 'email', 'limit@example.com', 'gmail', '127.0.0.1', "
                            + "now() - interval '2 minutes', 'sent')",
                    UUID.randomUUID(), challenge, userId);
        }
        List<Boolean> results = race(
                () -> db.queryForObject("SELECT reserve_otp_send(?, ?, 'email', 'limit@example.com', "
                        + "'gmail', '127.0.0.1'::inet, 20)", Boolean.class, challenge, userId),
                () -> db.queryForObject("SELECT reserve_otp_send(?, ?, 'email', 'limit@example.com', "
                        + "'gmail', '127.0.0.1'::inet, 20)", Boolean.class, challenge, userId));
        assertEquals(1, results.stream().filter(Boolean.TRUE::equals).count());
        assertEquals(5, db.queryForObject("SELECT count(*) FROM otp_send_attempt WHERE user_id = ? "
                + "AND attempted_at > now() - interval '1 hour'", Integer.class, userId));
    }

    @Test void resendReplacesOldOtpWithoutResettingHourlyLedger() {
        UUID userId = user("resend@example.com");
        UUID challenge = UUID.randomUUID();
        String oldHash = hash(), newHash = hash();
        db.update("INSERT INTO otp_challenge(challenge_id, user_id, purpose, channel, contact_kind, "
                        + "contact_value, verification_hash, issued_at, expires_at) VALUES (?, ?, "
                        + "'password_reset', 'gmail', 'email', 'resend@example.com', ?, now(), "
                        + "now() + interval '5 minutes')", challenge, userId, oldHash);
        assertEquals(Boolean.TRUE, db.queryForObject("SELECT reserve_otp_send(?, ?, 'email', "
                + "'resend@example.com', 'gmail', '127.0.0.1'::inet, 20)", Boolean.class, challenge, userId));
        db.update("UPDATE otp_send_attempt SET attempted_at = now() - interval '61 seconds' "
                + "WHERE challenge_id = ?", challenge);
        assertEquals(Boolean.TRUE, db.queryForObject("SELECT resend_user_otp(?, ?, ?, "
                + "'127.0.0.1'::inet, 20)", Boolean.class, challenge, userId, newHash));
        assertEquals(2, db.queryForObject("SELECT count(*) FROM otp_send_attempt WHERE challenge_id = ?",
                Integer.class, challenge));
        assertEquals("invalid", db.queryForObject("SELECT consume_user_otp(?, ?, 'password_reset', "
                + "'email', 'resend@example.com', 'gmail', ?)", String.class, userId, challenge, oldHash));
        assertEquals("consumed", db.queryForObject("SELECT consume_user_otp(?, ?, 'password_reset', "
                + "'email', 'resend@example.com', 'gmail', ?)", String.class, userId, challenge, newHash));
    }

    @Test void blockedResendCannotReviveExpiredChallenge() throws Exception {
        UUID userId = user("expires@example.com");
        UUID challenge = UUID.randomUUID();
        db.update("INSERT INTO otp_challenge(challenge_id, user_id, purpose, channel, contact_kind, "
                        + "contact_value, verification_hash, issued_at, expires_at) VALUES (?, ?, "
                        + "'password_reset', 'gmail', 'email', 'expires@example.com', ?, now(), "
                        + "now() + interval '2 seconds')", challenge, userId, hash());
        db.update("INSERT INTO otp_send_attempt(send_id, challenge_id, user_id, contact_kind, "
                        + "contact_value, channel, request_ip, attempted_at, delivery_state) "
                        + "VALUES (?, ?, ?, 'email', 'expires@example.com', 'gmail', '127.0.0.1', "
                        + "now() - interval '61 seconds', 'sent')", UUID.randomUUID(), challenge, userId);
        ArrayBlockingQueue<Integer> pids = new ArrayBlockingQueue<>(1);
        try (ExecutorService pool = Executors.newSingleThreadExecutor();
                Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lock = blocker.prepareStatement(
                    "SELECT challenge_id FROM otp_challenge WHERE challenge_id = ? FOR UPDATE")) {
                lock.setObject(1, challenge);
                lock.executeQuery().close();
            }
            Future<Boolean> waiting = pool.submit(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    pids.put(backendPid(connection));
                    boolean result;
                    try (PreparedStatement call = connection.prepareStatement(
                            "SELECT resend_user_otp(?, ?, ?, '127.0.0.1'::inet, 20)")) {
                        call.setObject(1, challenge);
                        call.setObject(2, userId);
                        call.setString(3, hash());
                        try (ResultSet rows = call.executeQuery()) {
                            rows.next();
                            result = rows.getBoolean(1);
                        }
                    }
                    connection.commit();
                    return result;
                }
            });
            Integer pid = pids.poll(5, TimeUnit.SECONDS);
            assertNotNull(pid);
            awaitDatabaseLock(pid);
            Thread.sleep(2300);
            blocker.commit();
            assertFalse(waiting.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, db.queryForObject("SELECT count(*) FROM otp_send_attempt WHERE challenge_id = ?",
                Integer.class, challenge));
    }

    @Test void concurrentReset() throws Exception {
        UUID userId = user("reset@example.com");
        db.update("UPDATE app_user SET phone = '+84912345678' WHERE user_id = ?", userId);
        UUID sessionId = session(userId);
        UUID challenge = UUID.randomUUID();
        UUID otherChallenge = UUID.randomUUID();
        UUID grant = UUID.randomUUID();
        UUID otherGrant = UUID.randomUUID();
        String grantHash = hash();
        String otherGrantHash = hash();
        db.update("INSERT INTO otp_challenge(challenge_id, user_id, purpose, channel, contact_kind, "
                        + "contact_value, verification_hash, issued_at, expires_at, used_at) VALUES (?, ?, "
                        + "'password_reset', 'gmail', 'email', 'reset@example.com', ?, now(), "
                        + "now() + interval '5 minutes', now())", challenge, userId, hash());
        db.update("INSERT INTO password_reset_grant(grant_id, user_id, challenge_id, grant_hash, "
                        + "issued_at, expires_at) VALUES (?, ?, ?, ?, now(), now() + interval '5 minutes')",
                grant, userId, challenge, grantHash);
        db.update("INSERT INTO otp_challenge(challenge_id, user_id, purpose, channel, contact_kind, "
                        + "contact_value, verification_hash, issued_at, expires_at, used_at) VALUES (?, ?, "
                        + "'password_reset', 'zalo', 'phone', '+84912345678', ?, now(), "
                        + "now() + interval '5 minutes', now())", otherChallenge, userId, hash());
        db.update("INSERT INTO password_reset_grant(grant_id, user_id, challenge_id, grant_hash, "
                        + "issued_at, expires_at) VALUES (?, ?, ?, ?, now(), now() + interval '5 minutes')",
                otherGrant, userId, otherChallenge, otherGrantHash);
        List<Boolean> results = race(
                () -> db.queryForObject("SELECT complete_password_reset(?, ?)", Boolean.class,
                        grantHash, "new-hash-a"),
                () -> db.queryForObject("SELECT complete_password_reset(?, ?)", Boolean.class,
                        otherGrantHash, "new-hash-b"));
        assertEquals(1, results.stream().filter(Boolean.TRUE::equals).count());
        assertEquals(1, db.queryForObject("SELECT count(*) FROM password_reset_grant WHERE user_id = ? "
                + "AND consumed_at IS NOT NULL", Integer.class, userId));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM password_reset_grant WHERE user_id = ? "
                + "AND invalidated_at IS NOT NULL", Integer.class, userId));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM user_session WHERE session_id = ? "
                + "AND revoked_at IS NOT NULL", Integer.class, sessionId));
    }

    @Test void concurrentContactClaim() throws Exception {
        String firstEmail = "first" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        String secondEmail = "second" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        UUID first = user(firstEmail);
        UUID second = user(secondEmail);
        db.update("UPDATE app_user SET email_verified_at = now() WHERE user_id IN (?, ?)", first, second);
        String candidate = "claim" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        UUID firstChange = UUID.randomUUID(), secondChange = UUID.randomUUID();
        UUID firstChallenge = UUID.randomUUID(), secondChallenge = UUID.randomUUID();
        String firstProof = hash(), secondProof = hash();
        for (int i = 0; i < 2; i++) {
            UUID owner = i == 0 ? first : second;
            UUID change = i == 0 ? firstChange : secondChange;
            UUID challenge = i == 0 ? firstChallenge : secondChallenge;
            String proof = i == 0 ? firstProof : secondProof;
            UUID sessionId = session(owner);
            UUID reauthId = UUID.randomUUID();
            db.update("INSERT INTO reauth_proof(proof_id, user_id, session_id, operation, proof_hash, "
                            + "issued_at, expires_at, consumed_at) VALUES (?, ?, ?, 'contact_change', ?, "
                            + "now(), now() + interval '5 minutes', now())",
                    reauthId, owner, sessionId, hash());
            db.update("INSERT INTO pending_contact_change(change_id, user_id, contact_kind, candidate_value, "
                            + "channel, reauth_proof_id, created_at, expires_at) VALUES (?, ?, 'email', ?, 'gmail', ?, now(), "
                            + "now() + interval '10 minutes')", change, owner, candidate, reauthId);
            db.update("INSERT INTO otp_challenge(challenge_id, user_id, change_id, purpose, channel, "
                            + "contact_kind, contact_value, verification_hash, issued_at, expires_at) "
                            + "VALUES (?, ?, ?, 'contact_change', 'gmail', 'email', ?, ?, now(), "
                            + "now() + interval '5 minutes')", challenge, owner, change, candidate, proof);
        }
        assertEquals(firstEmail, db.queryForObject("SELECT email FROM app_user WHERE user_id = ?",
                String.class, first));
        assertEquals(secondEmail, db.queryForObject("SELECT email FROM app_user WHERE user_id = ?",
                String.class, second));
        List<Boolean> results = race(
                () -> db.queryForObject("SELECT complete_contact_change(?, ?, ?)", Boolean.class,
                        firstChange, firstChallenge, firstProof),
                () -> db.queryForObject("SELECT complete_contact_change(?, ?, ?)", Boolean.class,
                        secondChange, secondChallenge, secondProof));
        assertEquals(1, results.stream().filter(Boolean.TRUE::equals).count());
        assertEquals(1, db.queryForObject("SELECT count(*) FROM app_user WHERE lower(email) = lower(?) "
                + "AND email_verified_at IS NOT NULL", Integer.class, candidate));
        UUID winner = db.queryForObject("SELECT user_id FROM app_user WHERE email = ?", UUID.class, candidate);
        String oldEmail = winner.equals(first) ? firstEmail : secondEmail;
        assertEquals(0, db.queryForObject("SELECT count(*) FROM verified_contact_claim "
                + "WHERE contact_kind = 'email' AND normalized_value = ?", Integer.class, oldEmail));
        assertEquals(0, db.queryForObject("SELECT count(*) FROM user_session WHERE user_id = ? "
                + "AND revoked_at IS NULL", Integer.class, winner));
    }

    @Test void contactStartRequiresFreshOneTimeReauthAndChecksExistingClaim() {
        String held = "held" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        UUID owner = user(held);
        db.update("UPDATE app_user SET email_verified_at = now() WHERE user_id = ?", owner);
        UUID changer = user("current@example.com");
        UUID sessionId = session(changer);
        UUID proofId = UUID.randomUUID();
        String proofHash = hash();
        db.update("INSERT INTO reauth_proof(proof_id, user_id, session_id, operation, proof_hash, "
                        + "issued_at, expires_at) VALUES (?, ?, ?, 'contact_change', ?, now(), "
                        + "now() + interval '5 minutes')", proofId, changer, sessionId, proofHash);
        assertNull(db.queryForObject("SELECT start_contact_change(?, ?, ?, 'email', ?, 'gmail')",
                UUID.class, changer, sessionId, proofHash, held));
        String candidate = "free" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        UUID changeId = db.queryForObject("SELECT start_contact_change(?, ?, ?, 'email', ?, 'gmail')",
                UUID.class, changer, sessionId, proofHash, candidate);
        assertNotNull(changeId);
        assertNull(db.queryForObject("SELECT start_contact_change(?, ?, ?, 'email', ?, 'gmail')",
                UUID.class, changer, sessionId, proofHash, candidate));
        assertEquals("current@example.com", db.queryForObject("SELECT email FROM app_user WHERE user_id = ?",
                String.class, changer));
    }

    @Test void blockedContactCompletionCannotPassExpiredPendingChange() throws Exception {
        UUID userId = user("oldcontact@example.com");
        UUID sessionId = session(userId);
        UUID proofId = UUID.randomUUID();
        String proofHash = hash();
        db.update("INSERT INTO reauth_proof(proof_id, user_id, session_id, operation, proof_hash, "
                        + "issued_at, expires_at) VALUES (?, ?, ?, 'contact_change', ?, now(), "
                        + "now() + interval '5 minutes')", proofId, userId, sessionId, proofHash);
        String candidate = "pending" + UUID.randomUUID().toString().substring(0, 8) + "@gmail.com";
        UUID change = db.queryForObject("SELECT start_contact_change(?, ?, ?, 'email', ?, 'gmail')",
                UUID.class, userId, sessionId, proofHash, candidate);
        assertNotNull(change);
        db.update("UPDATE pending_contact_change SET expires_at = now() + interval '5 seconds' "
                + "WHERE change_id = ?", change);
        UUID challenge = UUID.randomUUID();
        String otpHash = hash();
        db.update("INSERT INTO otp_challenge(challenge_id, user_id, change_id, purpose, channel, "
                        + "contact_kind, contact_value, verification_hash, issued_at, expires_at) "
                        + "VALUES (?, ?, ?, 'contact_change', 'gmail', 'email', ?, ?, now(), "
                        + "now() + interval '5 minutes')", challenge, userId, change, candidate, otpHash);
        ArrayBlockingQueue<Integer> pids = new ArrayBlockingQueue<>(1);
        try (ExecutorService pool = Executors.newSingleThreadExecutor();
                Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (PreparedStatement lock = blocker.prepareStatement(
                    "SELECT challenge_id FROM otp_challenge WHERE challenge_id = ? FOR UPDATE")) {
                lock.setObject(1, challenge);
                lock.executeQuery().close();
            }
            Future<Boolean> waiting = pool.submit(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    connection.setAutoCommit(false);
                    pids.put(backendPid(connection));
                    boolean result;
                    try (PreparedStatement call = connection.prepareStatement(
                            "SELECT complete_contact_change(?, ?, ?)")) {
                        call.setObject(1, change);
                        call.setObject(2, challenge);
                        call.setString(3, otpHash);
                        try (ResultSet rows = call.executeQuery()) {
                            rows.next();
                            result = rows.getBoolean(1);
                        }
                    }
                    connection.commit();
                    return result;
                }
            });
            Integer pid = pids.poll(5, TimeUnit.SECONDS);
            assertNotNull(pid);
            awaitDatabaseLock(pid);
            Thread.sleep(5300);
            blocker.commit();
            assertFalse(waiting.get(10, TimeUnit.SECONDS));
        }
        assertEquals("oldcontact@example.com", db.queryForObject(
                "SELECT email FROM app_user WHERE user_id = ?", String.class, userId));
    }

    @Test void idempotencyReceiptRequiresMinimumRetentionAndScopedKey() {
        UUID userId = user(null);
        UUID key = UUID.randomUUID();
        String bodyHash = hash();
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> db.update("INSERT INTO idempotency_receipt(user_id, endpoint, idempotency_key, "
                        + "body_sha256, created_at, expires_at) VALUES (?, '/me', ?, ?, now(), "
                        + "now() + interval '23 hours')", userId, key, bodyHash));
        db.update("INSERT INTO idempotency_receipt(user_id, endpoint, idempotency_key, "
                + "body_sha256, created_at, expires_at) VALUES (?, '/me', ?, ?, now(), "
                + "now() + interval '25 hours')", userId, key, bodyHash);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> db.update("INSERT INTO idempotency_receipt(user_id, endpoint, idempotency_key, "
                        + "body_sha256, created_at, expires_at) VALUES (?, '/me', ?, ?, now(), "
                        + "now() + interval '25 hours')", userId, key, hash()));
        db.update("INSERT INTO idempotency_receipt(user_id, endpoint, idempotency_key, "
                + "body_sha256, created_at, expires_at) VALUES (?, '/me/password-change', ?, ?, now(), "
                + "now() + interval '25 hours')", userId, key, bodyHash);
    }
}
