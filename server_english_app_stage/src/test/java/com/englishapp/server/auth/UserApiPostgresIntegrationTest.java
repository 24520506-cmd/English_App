package com.englishapp.server.auth;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_DATABASE_URL", matches = ".+")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserApiPostgresIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("TEST_DATABASE_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DATABASE_USER", "english_app"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DATABASE_PASSWORD", ""));
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach void resetLoopbackLoginRateLimit() {
        // All embedded-server test requests originate from 127.0.0.1. Keep the
        // production IP throttle intact while isolating each integration test.
        db.update("DELETE FROM login_ip_request WHERE ip_address = '127.0.0.1'");
    }

    private HttpResponse<String> request(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", "application/json");
        if (token != null) builder.header("Authorization", "Bearer " + token);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String register() throws Exception {
        String username = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        HttpResponse<String> response = request("POST", "/users", null,
                "{\"username\":\"" + username + "\",\"password\":\"Password123!\"}");
        assertEquals(201, response.statusCode(), response.body());
        assertEquals("learner", JsonPath.read(response.body(), "$.role"));
        return username;
    }

    private HttpResponse<String> login(String username, String device) throws Exception {
        return request("POST", "/auth/login", null, "{\"username\":\"" + username
                + "\",\"password\":\"Password123!\",\"device_label\":\"" + device
                + "\",\"platform\":\"android\"}");
    }

    @Test void profileUpdatePersistsAndRejectsContactPatch() throws Exception {
        String username = register();
        String access = JsonPath.read(login(username, "deviceA").body(), "$.access_token");
        HttpResponse<String> update = request("PATCH", "/me", access, "{\"display_name\":\"Minh\"}");
        assertEquals(200, update.statusCode(), update.body());
        assertEquals("Minh", JsonPath.read(request("GET", "/me", access, null).body(), "$.profile.display_name"));
        HttpResponse<String> forbiddenField = request("PATCH", "/me", access, "{\"email\":\"attacker@example.com\"}");
        assertEquals(400, forbiddenField.statusCode(), forbiddenField.body());
        assertEquals("VALIDATION_ERROR", JsonPath.read(forbiddenField.body(), "$.error.code"));
        String saved = db.queryForObject("SELECT display_name FROM app_user WHERE username = ?", String.class, username);
        assertEquals("Minh", saved);
    }

    @Test void profileDisplayNameIsReturnedBySubsequentRead() throws Exception {
        String username = register();
        String access = JsonPath.read(login(username, "profile-update").body(), "$.access_token");

        HttpResponse<String> updated = request("PATCH", "/me", access, "{\"display_name\":\"Minh\"}");
        assertEquals(200, updated.statusCode(), updated.body());
        assertEquals("Minh", JsonPath.read(updated.body(), "$.profile.display_name"));
        assertEquals("Minh", JsonPath.read(request("GET", "/me", access, null).body(),
                "$.profile.display_name"));
        HttpResponse<String> cleared = request("PATCH", "/me", access, "{\"display_name\":null}");
        assertEquals(200, cleared.statusCode(), cleared.body());
        assertNull(JsonPath.read(request("GET", "/me", access, null).body(), "$.profile.display_name"));
    }

    @Test void preferencesAreSavedReadBackAndIsolatedByBearerUser() throws Exception {
        String userA = register();
        String userB = register();
        String accessA = JsonPath.read(login(userA, "preferences-A").body(), "$.access_token");
        String accessB = JsonPath.read(login(userB, "preferences-B").body(), "$.access_token");
        String bodyA = "{\"band\":\"BELOW_4_0\",\"topic_ids\":[\"daily_life\",\"finance\"]}";

        HttpResponse<String> savedA = request("PUT", "/me/preferences", accessA, bodyA);
        assertEquals(200, savedA.statusCode(), savedA.body());
        assertEquals("BELOW_4_0", JsonPath.read(savedA.body(), "$.band"));
        assertEquals(List.of("daily_life", "finance"), JsonPath.read(savedA.body(), "$.topic_ids"));
        assertEquals("BELOW_4_0", JsonPath.read(request("GET", "/me/preferences", accessA, null).body(),
                "$.band"));
        assertEquals(List.of("daily_life", "finance"), JsonPath.read(
                request("GET", "/me/preferences", accessA, null).body(), "$.topic_ids"));
        HttpResponse<String> emptyB = request("GET", "/me/preferences", accessB, null);
        assertEquals(200, emptyB.statusCode(), emptyB.body());
        assertNull(JsonPath.read(emptyB.body(), "$.band"));
        assertEquals(List.of(), JsonPath.read(emptyB.body(), "$.topic_ids"));
        HttpResponse<String> savedB = request("PUT", "/me/preferences", accessB,
                "{\"band\":\"7_0_PLUS\",\"topic_ids\":[\"health\"]}");
        assertEquals(200, savedB.statusCode(), savedB.body());
        assertEquals("BELOW_4_0", JsonPath.read(request("GET", "/me/preferences", accessA, null).body(),
                "$.band"));
        assertEquals(List.of("daily_life", "finance"), JsonPath.read(
                request("GET", "/me/preferences", accessA, null).body(), "$.topic_ids"));
        HttpResponse<String> replacedA = request("PUT", "/me/preferences", accessA,
                "{\"band\":\"5_5_6_5\",\"topic_ids\":[\"travel\"]}");
        assertEquals(200, replacedA.statusCode(), replacedA.body());
        assertEquals("5_5_6_5", JsonPath.read(replacedA.body(), "$.band"));
        assertEquals(List.of("travel"), JsonPath.read(replacedA.body(), "$.topic_ids"));
    }

    @Test void preferencesRejectInvalidBandAndTopicWithoutReplacingSavedChoices() throws Exception {
        String username = register();
        String access = JsonPath.read(login(username, "preferences-invalid").body(), "$.access_token");
        assertEquals(200, request("PUT", "/me/preferences", access,
                "{\"band\":\"4_0_5_0\",\"topic_ids\":[\"education\"]}").statusCode());

        HttpResponse<String> invalidBand = request("PUT", "/me/preferences", access,
                "{\"band\":\"4.5\",\"topic_ids\":[\"finance\"]}");
        assertEquals(400, invalidBand.statusCode(), invalidBand.body());
        assertEquals("VALIDATION_ERROR", JsonPath.read(invalidBand.body(), "$.error.code"));
        HttpResponse<String> invalidTopic = request("PUT", "/me/preferences", access,
                "{\"band\":\"5_5_6_5\",\"topic_ids\":[\"not_a_topic\"]}");
        assertEquals(400, invalidTopic.statusCode(), invalidTopic.body());
        assertEquals("VALIDATION_ERROR", JsonPath.read(invalidTopic.body(), "$.error.code"));

        HttpResponse<String> unchanged = request("GET", "/me/preferences", access, null);
        assertEquals("4_0_5_0", JsonPath.read(unchanged.body(), "$.band"));
        assertEquals(List.of("education"), JsonPath.read(unchanged.body(), "$.topic_ids"));
    }

    @Test void preferencesRequireValidBearerToken() throws Exception {
        for (String path : List.of("/me/preferences")) {
            assertEquals(401, request("GET", path, null, null).statusCode());
            assertEquals(401, request("PUT", path, null,
                    "{\"band\":\"BELOW_4_0\",\"topic_ids\":[\"daily_life\"]}").statusCode());
            assertEquals(401, request("GET", path, "not-a-valid-token", null).statusCode());
            assertEquals(401, request("PUT", path, "not-a-valid-token",
                    "{\"band\":\"BELOW_4_0\",\"topic_ids\":[\"daily_life\"]}").statusCode());
        }
    }

    @Test void preferencesAreRestrictedToLearnerRole() throws Exception {
        String username = register();
        db.update("UPDATE app_user SET role = 'admin' WHERE username = ?", username);
        String adminAccess = JsonPath.read(login(username, "admin-preferences").body(), "$.access_token");
        assertEquals(403, request("GET", "/me/preferences", adminAccess, null).statusCode());
        assertEquals(403, request("PUT", "/me/preferences", adminAccess,
                "{\"band\":\"BELOW_4_0\",\"topic_ids\":[\"daily_life\"]}").statusCode());
    }

    @Test void sessionsAreOwnedAndRevocationPreventsRefresh() throws Exception {
        String owner = register();
        String other = register();
        HttpResponse<String> first = login(owner, "deviceA");
        HttpResponse<String> second = login(owner, "deviceB");
        String firstAccess = JsonPath.read(first.body(), "$.access_token");
        String secondAccess = JsonPath.read(second.body(), "$.access_token");
        String secondRefresh = JsonPath.read(second.body(), "$.refresh_token");
        String secondId = JsonPath.read(second.body(), "$.session.session_id");
        String otherAccess = JsonPath.read(login(other, "deviceC").body(), "$.access_token");
        HttpResponse<String> listing = request("GET", "/me/sessions", firstAccess, null);
        assertEquals(200, listing.statusCode(), listing.body());
        assertEquals(2, ((java.util.List<?>) JsonPath.read(listing.body(), "$.sessions")).size());
        assertEquals(404, request("DELETE", "/me/sessions/" + secondId, otherAccess, null).statusCode());
        assertEquals(204, request("DELETE", "/me/sessions/" + secondId, firstAccess, null).statusCode());
        assertEquals(401, request("GET", "/me", secondAccess, null).statusCode());
        assertEquals(401, request("POST", "/auth/refresh", null,
                "{\"refresh_token\":\"" + secondRefresh + "\"}").statusCode());
        assertEquals(1, db.queryForObject("SELECT count(*) FROM user_session WHERE user_id = "
                + "(SELECT user_id FROM app_user WHERE username = ?) AND revoked_at IS NULL", Integer.class, owner));
    }

    @Test void passwordChangeRevokesAllSessionsAndRequiresNewPassword() throws Exception {
        String username = register();
        String firstAccess = JsonPath.read(login(username, "deviceA").body(), "$.access_token");
        String secondAccess = JsonPath.read(login(username, "deviceB").body(), "$.access_token");
        HttpResponse<String> wrong = request("POST", "/me/password-change", firstAccess,
                "{\"current_password\":\"wrong\",\"new_password\":\"NewPassword123!\"}");
        assertEquals(401, wrong.statusCode(), wrong.body());
        assertEquals(204, request("POST", "/me/password-change", firstAccess,
                "{\"current_password\":\"Password123!\",\"new_password\":\"NewPassword123!\"}").statusCode());
        assertEquals(401, request("GET", "/me", firstAccess, null).statusCode());
        assertEquals(401, request("GET", "/me", secondAccess, null).statusCode());
        assertEquals(401, login(username, "oldPassword").statusCode());
        HttpResponse<String> replacement = request("POST", "/auth/login", null,
                "{\"username\":\"" + username + "\",\"password\":\"NewPassword123!\","
                + "\"device_label\":\"deviceD\",\"platform\":\"android\"}");
        assertEquals(200, replacement.statusCode(), replacement.body());
    }

    @Test void revokeAllAlsoInvalidatesRefreshTokens() throws Exception {
        String username = register();
        HttpResponse<String> first = login(username, "deviceA");
        HttpResponse<String> second = login(username, "deviceB");
        String access = JsonPath.read(first.body(), "$.access_token");
        String refresh = JsonPath.read(second.body(), "$.refresh_token");
        assertEquals(204, request("POST", "/me/sessions/revoke-all", access, null).statusCode());
        assertEquals(401, request("GET", "/me", access, null).statusCode());
        assertEquals(401, request("POST", "/auth/refresh", null,
                "{\"refresh_token\":\"" + refresh + "\"}").statusCode());
        assertEquals(0, db.queryForObject("SELECT count(*) FROM user_session WHERE user_id = "
                + "(SELECT user_id FROM app_user WHERE username = ?) AND revoked_at IS NULL", Integer.class, username));
    }

    @Test void refreshAndRevokeAllRace() throws Exception {
        String username = register();
        String revokerAccess = JsonPath.read(login(username, "revoker").body(), "$.access_token");
        String rotatingRefresh = JsonPath.read(login(username, "rotating").body(), "$.refresh_token");
        CountDownLatch start = new CountDownLatch(1);
        HttpResponse<String> revoke;
        HttpResponse<String> refresh;
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<HttpResponse<String>> revokeFuture = pool.submit(() -> {
                start.await();
                return request("POST", "/me/sessions/revoke-all", revokerAccess, null);
            });
            Future<HttpResponse<String>> refreshFuture = pool.submit(() -> {
                start.await();
                return request("POST", "/auth/refresh", null,
                        "{\"refresh_token\":\"" + rotatingRefresh + "\"}");
            });
            start.countDown();
            revoke = revokeFuture.get(10, TimeUnit.SECONDS);
            refresh = refreshFuture.get(10, TimeUnit.SECONDS);
        }
        assertEquals(204, revoke.statusCode(), revoke.body());
        assertTrue(List.of(200, 401).contains(refresh.statusCode()), refresh.body());
        if (refresh.statusCode() == 200) {
            String issuedAccess = JsonPath.read(refresh.body(), "$.access_token");
            String issuedRefresh = JsonPath.read(refresh.body(), "$.refresh_token");
            assertEquals(401, request("GET", "/me", issuedAccess, null).statusCode());
            assertEquals(401, request("POST", "/auth/refresh", null,
                    "{\"refresh_token\":\"" + issuedRefresh + "\"}").statusCode());
        }
        assertEquals(0, db.queryForObject("SELECT count(*) FROM user_session WHERE user_id = "
                + "(SELECT user_id FROM app_user WHERE username = ?) AND revoked_at IS NULL", Integer.class, username));
    }

    @Test void refreshReuseRevokesSessionAndWritesSecurityEvent() throws Exception {
        String username = register();
        HttpResponse<String> signedIn = login(username, "deviceA");
        String oldRefresh = JsonPath.read(signedIn.body(), "$.refresh_token");
        String sessionId = JsonPath.read(signedIn.body(), "$.session.session_id");
        HttpResponse<String> rotated = request("POST", "/auth/refresh", null,
                "{\"refresh_token\":\"" + oldRefresh + "\"}");
        assertEquals(200, rotated.statusCode(), rotated.body());
        String newAccess = JsonPath.read(rotated.body(), "$.access_token");
        HttpResponse<String> reused = request("POST", "/auth/refresh", null,
                "{\"refresh_token\":\"" + oldRefresh + "\"}");
        assertEquals(401, reused.statusCode(), reused.body());
        assertEquals("REFRESH_REUSE_DETECTED", JsonPath.read(reused.body(), "$.error.code"));
        assertEquals(401, request("GET", "/me", newAccess, null).statusCode());
        assertEquals(1, db.queryForObject("SELECT count(*) FROM security_event WHERE session_id = ?::uuid "
                + "AND event_type = 'refresh_token_reuse'", Integer.class, sessionId));
        assertEquals(1, db.queryForObject("SELECT count(*) FROM retired_refresh_token "
                + "WHERE session_id = ?::uuid AND replaced_by_hash IS NOT NULL", Integer.class, sessionId));
    }

    @Test void logoutAndSingleRevokeDoNotDeadlockWithRevokeAll() throws Exception {
        for (boolean logout : List.of(true, false)) {
            String username = register();
            String ownerAccess = JsonPath.read(login(username, "owner").body(), "$.access_token");
            HttpResponse<String> targetLogin = login(username, "target");
            String targetAccess = JsonPath.read(targetLogin.body(), "$.access_token");
            String targetId = JsonPath.read(targetLogin.body(), "$.session.session_id");
            CountDownLatch start = new CountDownLatch(1);
            HttpResponse<String> all;
            HttpResponse<String> single;
            try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
                Future<HttpResponse<String>> allFuture = pool.submit(() -> {
                    start.await();
                    return request("POST", "/me/sessions/revoke-all", ownerAccess, null);
                });
                Future<HttpResponse<String>> singleFuture = pool.submit(() -> {
                    start.await();
                    return logout ? request("POST", "/auth/logout", targetAccess, null)
                            : request("DELETE", "/me/sessions/" + targetId, targetAccess, null);
                });
                start.countDown();
                all = allFuture.get(10, TimeUnit.SECONDS);
                single = singleFuture.get(10, TimeUnit.SECONDS);
            }
            assertEquals(204, all.statusCode(), all.body());
            assertTrue(List.of(204, 401, 404).contains(single.statusCode()), single.body());
            assertEquals(0, db.queryForObject("SELECT count(*) FROM user_session WHERE user_id = "
                    + "(SELECT user_id FROM app_user WHERE username = ?) AND revoked_at IS NULL",
                    Integer.class, username));
        }
    }
}
