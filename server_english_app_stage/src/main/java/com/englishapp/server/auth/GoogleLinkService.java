package com.englishapp.server.auth;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.json.JsonMapper;

@Service
final class GoogleLinkService {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String JWKS_ENDPOINT = "https://www.googleapis.com/oauth2/v3/certs";

    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final String clientId;
    private final String clientSecret;
    private final String callbackUri;
    private final String mobileReturnUri;
    private final byte[] encryptionKey;
    private final RestClient http = RestClient.create();

    GoogleLinkService(JdbcTemplate db, PasswordEncoder passwords,
            @Value("${app.google.client-id:}") String clientId,
            @Value("${app.google.client-secret:}") String clientSecret,
            @Value("${app.google.callback-uri:}") String callbackUri,
            @Value("${app.google.mobile-return-uri:}") String mobileReturnUri,
            @Value("${app.google.pkce-encryption-key:}") String encryptionKey) {
        this.db = db;
        this.passwords = passwords;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.callbackUri = callbackUri;
        this.mobileReturnUri = mobileReturnUri;
        this.encryptionKey = decodeKey(encryptionKey);
    }

    @Transactional
    Map<String, Object> start(String accessToken, String currentPassword, UUID pendingChangeId) {
        ensureConfigured();
        if (accessToken == null || accessToken.isBlank() || currentPassword == null)
            throw validationFailure();
        var session = db.query("SELECT s.session_id,s.user_id,s.last_activity_at,s.absolute_expires_at,"
                        + "s.access_expires_at,s.revoked_at,s.idle_timeout_seconds,u.password_hash "
                        + "FROM user_session s JOIN app_user u ON u.user_id=s.user_id "
                        + "WHERE s.access_token_hash=? AND u.status='active'",
                (rs, ignored) -> new GoogleSession((UUID) rs.getObject("session_id"),
                        (UUID) rs.getObject("user_id"), rs.getTimestamp("last_activity_at").toInstant(),
                        rs.getTimestamp("absolute_expires_at").toInstant(),
                        rs.getTimestamp("access_expires_at").toInstant(),
                        rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
                        rs.getInt("idle_timeout_seconds"), rs.getString("password_hash")),
                TokenCodec.sha256(accessToken)).stream().findFirst().orElse(null);
        if (session == null || !session.isLive()) throw invalidSession();
        db.queryForObject("SELECT user_id FROM app_user WHERE user_id=? FOR UPDATE", UUID.class, session.userId());
        if (!passwords.matches(currentPassword, session.passwordHash())) throw ApiFailure.invalidCredentials();

        if (pendingChangeId != null) {
            Boolean validChange = db.queryForObject("SELECT EXISTS(SELECT 1 FROM pending_contact_change "
                            + "WHERE change_id=? AND user_id=? AND contact_kind='email' AND channel='gmail' "
                            + "AND completed_at IS NULL AND invalidated_at IS NULL AND expires_at>now())",
                    Boolean.class, pendingChangeId, session.userId());
            if (!Boolean.TRUE.equals(validChange)) throw new ApiFailure("NOT_FOUND", HttpStatus.NOT_FOUND,
                    "Không tìm thấy yêu cầu đổi email đang chờ.");
        }

        UUID proofId = UUID.randomUUID();
        String proofToken = TokenCodec.newToken();
        db.update("INSERT INTO reauth_proof(proof_id,user_id,session_id,operation,proof_hash,issued_at,expires_at,"
                        + "consumed_at) VALUES (?,?,?,'google_link',?,now(),now()+interval '5 minutes',now())",
                proofId, session.userId(), session.sessionId(), TokenCodec.sha256(proofToken));
        db.update("UPDATE google_link_attempt SET consumed_at=now() WHERE user_id=? "
                + "AND consumed_at IS NULL AND expires_at>now()", session.userId());
        String state = randomValue(32);
        String nonce = randomValue(32);
        String verifier = randomValue(32);
        String challenge = base64Url(sha256(verifier.getBytes(StandardCharsets.US_ASCII)));
        byte[] encryptionNonce = new byte[12];
        RANDOM.nextBytes(encryptionNonce);
        UUID attemptId = UUID.randomUUID();
        db.update("INSERT INTO google_link_attempt(attempt_id,user_id,session_id,reauth_proof_id,state_hash,"
                        + "nonce_hash,pkce_challenge,pkce_verifier_ciphertext,pkce_encryption_nonce,redirect_uri,"
                        + "pending_change_id,created_at,expires_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,now(),now()+interval '5 minutes')",
                attemptId, session.userId(), session.sessionId(), proofId, TokenCodec.sha256(state),
                TokenCodec.sha256(nonce), challenge, encrypt(verifier, encryptionNonce), encryptionNonce,
                callbackUri, pendingChangeId);
        String authorizationUrl = UriComponentsBuilder.fromUriString(AUTHORIZATION_ENDPOINT)
                .queryParam("client_id", clientId).queryParam("redirect_uri", callbackUri)
                .queryParam("response_type", "code").queryParam("scope", "openid email")
                .queryParam("state", state).queryParam("nonce", nonce)
                .queryParam("code_challenge", challenge).queryParam("code_challenge_method", "S256")
                .queryParam("prompt", "select_account").build().encode().toUriString();
        return Map.of("authorization_url", authorizationUrl,
                "expires_at", Instant.now().plusSeconds(300).toString());
    }

    @Transactional
    String callback(String code, String state, String providerError) {
        String result = "error";
        try {
            ensureConfigured();
            if (state == null || state.isBlank()) return redirect(result, "invalid_state");
            GoogleAttempt attempt = db.query("SELECT a.attempt_id,a.user_id,a.session_id,a.state_hash,a.nonce_hash,"
                            + "a.pkce_verifier_ciphertext,a.pkce_encryption_nonce,a.redirect_uri,a.pending_change_id,"
                            + "a.expires_at,"
                            + "a.consumed_at,s.last_activity_at,s.absolute_expires_at,s.idle_timeout_seconds,"
                            + "s.revoked_at,u.email,u.status "
                            + "FROM google_link_attempt a JOIN user_session s ON s.session_id=a.session_id "
                            + "JOIN app_user u ON u.user_id=a.user_id WHERE a.state_hash=? FOR UPDATE OF a,s,u",
                    (rs, ignored) -> new GoogleAttempt((UUID) rs.getObject("attempt_id"),
                            (UUID) rs.getObject("user_id"), (UUID) rs.getObject("session_id"),
                            rs.getString("state_hash"), rs.getString("nonce_hash"),
                            rs.getBytes("pkce_verifier_ciphertext"), rs.getBytes("pkce_encryption_nonce"),
                            rs.getString("redirect_uri"), (UUID) rs.getObject("pending_change_id"),
                            rs.getTimestamp("expires_at").toInstant(),
                            rs.getTimestamp("consumed_at") == null ? null : rs.getTimestamp("consumed_at").toInstant(),
                            rs.getTimestamp("last_activity_at").toInstant(),
                            rs.getTimestamp("absolute_expires_at").toInstant(), rs.getInt("idle_timeout_seconds"),
                            rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
                            rs.getString("email"), rs.getString("status")),
                    TokenCodec.sha256(state)).stream().findFirst().orElse(null);
            if (attempt == null || attempt.consumedAt() != null || !Instant.now().isBefore(attempt.expiresAt()))
                return redirect(result, "invalid_state");
            db.update("UPDATE google_link_attempt SET consumed_at=now() WHERE attempt_id=?", attempt.id());
            if (providerError != null || code == null || code.isBlank()) return redirect(result, "cancelled");
            if (!attempt.isSessionLive()) return redirect(result, "session_expired");

            String verifier = decrypt(attempt.encryptedVerifier(), attempt.encryptionNonce());
            Map<?, ?> tokenResponse = exchange(code, verifier, attempt.redirectUri());
            Object idToken = tokenResponse.get("id_token");
            if (!(idToken instanceof String jwt)) return redirect(result, "provider_error");
            GoogleIdentity identity = verifyIdToken(jwt, attempt.nonceHash());
            if (attempt.pendingChangeId() != null) {
                Boolean changed = db.queryForObject("SELECT complete_google_contact_change(?,?,?,?)", Boolean.class,
                        attempt.id(), attempt.pendingChangeId(), identity.subject(), identity.email());
                if (!Boolean.TRUE.equals(changed)) return redirect(result, "contact_change_failed");
                return redirect("contact_changed", null);
            }
            if (attempt.email() == null || !attempt.email().trim().equalsIgnoreCase(identity.email()))
                return redirect(result, "email_mismatch");
            Boolean linked = db.queryForObject("SELECT complete_google_link(?,?,?)", Boolean.class,
                    attempt.id(), identity.subject(), identity.email());
            if (!Boolean.TRUE.equals(linked)) return redirect(result, "contact_already_linked");
            result = "linked";
            return redirect(result, null);
        } catch (ApiFailure failure) {
            return redirect(result, "provider_error");
        } catch (RestClientException failure) {
            return redirect(result, "provider_error");
        }
    }

    private Map<?, ?> exchange(String code, String verifier, String redirect) {
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirect);
        form.add("code_verifier", verifier);
        return http.post().uri(TOKEN_ENDPOINT).contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(Map.class);
    }

    private GoogleIdentity verifyIdToken(String jwt, String expectedNonceHash) {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) throw invalidGoogleProof();
        try {
            Map<?, ?> header = JSON.readValue(Base64.getUrlDecoder().decode(parts[0]), Map.class);
            if (!"RS256".equals(header.get("alg")) || !(header.get("kid") instanceof String keyId))
                throw invalidGoogleProof();
            Map<?, ?> payload = JSON.readValue(Base64.getUrlDecoder().decode(parts[1]), Map.class);
            Map<?, ?> jwks = http.get().uri(JWKS_ENDPOINT).retrieve().body(Map.class);
            Object rawKeys = jwks == null ? null : jwks.get("keys");
            if (!(rawKeys instanceof List<?> keys)) throw invalidGoogleProof();
            Map<?, ?> jwk = keys.stream().filter(Map.class::isInstance).map(Map.class::cast)
                    .filter(key -> keyId.equals(key.get("kid"))).findFirst().orElseThrow(GoogleLinkService::invalidGoogleProof);
            byte[] modulus = Base64.getUrlDecoder().decode((String) jwk.get("n"));
            byte[] exponent = Base64.getUrlDecoder().decode((String) jwk.get("e"));
            var rsaKey = KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(
                    new java.math.BigInteger(1, modulus), new java.math.BigInteger(1, exponent)));
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(rsaKey);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(Base64.getUrlDecoder().decode(parts[2]))) throw invalidGoogleProof();

            Object issuer = payload.get("iss");
            if (!"https://accounts.google.com".equals(issuer) && !"accounts.google.com".equals(issuer))
                throw invalidGoogleProof();
            Object audience = payload.get("aud");
            boolean audienceMatches = clientId.equals(audience)
                    || audience instanceof List<?> list && list.contains(clientId);
            if (!audienceMatches) throw invalidGoogleProof();
            long now = Instant.now().getEpochSecond();
            long expiry = ((Number) payload.get("exp")).longValue();
            if (now >= expiry || payload.get("iat") instanceof Number issuedAt && issuedAt.longValue() > now + 60
                    || payload.get("nbf") instanceof Number notBefore && notBefore.longValue() > now + 60)
                throw invalidGoogleProof();
            if (audience instanceof List<?> audienceList && audienceList.size() > 1
                    && !clientId.equals(payload.get("azp"))) throw invalidGoogleProof();
            String nonce = (String) payload.get("nonce");
            if (nonce == null || !MessageDigest.isEqual(expectedNonceHash.getBytes(StandardCharsets.US_ASCII),
                    TokenCodec.sha256(nonce).getBytes(StandardCharsets.US_ASCII))) throw invalidGoogleProof();
            Object verified = payload.get("email_verified");
            if (!(Boolean.TRUE.equals(verified) || "true".equals(verified))) throw invalidGoogleProof();
            String email = String.valueOf(payload.get("email")).trim().toLowerCase(Locale.ROOT);
            String subject = (String) payload.get("sub");
            if (!email.matches("[^\\s@]+@gmail\\.com") || subject == null || subject.isBlank())
                throw invalidGoogleProof();
            return new GoogleIdentity(subject, email);
        } catch (ApiFailure failure) {
            throw failure;
        } catch (Exception failure) {
            throw invalidGoogleProof();
        }
    }

    private byte[] encrypt(String plaintext, byte[] nonce) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(128, nonce));
            return cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        } catch (Exception failure) {
            throw new IllegalStateException("Could not encrypt PKCE verifier", failure);
        }
    }

    private String decrypt(byte[] ciphertext, byte[] nonce) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"),
                    new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception failure) {
            throw invalidGoogleProof();
        }
    }

    private void ensureConfigured() {
        if (clientId.isBlank() || clientSecret.isBlank() || callbackUri.isBlank()
                || mobileReturnUri.isBlank() || encryptionKey.length != 32)
            throw new ApiFailure("GOOGLE_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
                    "Liên kết Google hiện chưa được cấu hình.");
        URI callback = URI.create(callbackUri);
        if (!"https".equalsIgnoreCase(callback.getScheme()))
            throw new ApiFailure("GOOGLE_NOT_CONFIGURED", HttpStatus.SERVICE_UNAVAILABLE,
                    "Liên kết Google hiện chưa được cấu hình.");
    }

    private String redirect(String result, String error) {
        var builder = UriComponentsBuilder.fromUriString(mobileReturnUri).queryParam("result", result);
        if (error != null) builder.queryParam("error", error);
        return builder.build().encode().toUriString();
    }

    private static byte[] decodeKey(String value) {
        if (value == null || value.isBlank()) return new byte[0];
        try { return Base64.getDecoder().decode(value); }
        catch (IllegalArgumentException invalid) { return new byte[0]; }
    }

    private static String randomValue(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return base64Url(value);
    }

    private static byte[] sha256(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static ApiFailure invalidGoogleProof() {
        return new ApiFailure("GOOGLE_PROOF_INVALID", HttpStatus.BAD_REQUEST,
                "Không thể xác minh tài khoản Google.");
    }

    private static ApiFailure invalidSession() {
        return new ApiFailure("SESSION_INVALID", HttpStatus.UNAUTHORIZED,
                "Phiên không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.");
    }

    private static ApiFailure validationFailure() {
        return new ApiFailure("VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                "Vui lòng kiểm tra thông tin đã nhập.");
    }

    private record GoogleIdentity(String subject, String email) { }

    private record GoogleAttempt(UUID id, UUID userId, UUID sessionId, String stateHash, String nonceHash,
            byte[] encryptedVerifier, byte[] encryptionNonce, String redirectUri, UUID pendingChangeId,
            Instant expiresAt, Instant consumedAt, Instant lastActivity, Instant absoluteExpiry, int idleTimeoutSeconds,
            Instant revokedAt, String email, String status) {
        boolean isSessionLive() {
            Instant now = Instant.now();
            return "active".equals(status) && revokedAt == null && now.isBefore(absoluteExpiry)
                    && now.isBefore(lastActivity.plusSeconds(idleTimeoutSeconds));
        }
    }

    private record GoogleSession(UUID sessionId, UUID userId, Instant lastActivity, Instant absoluteExpiry,
            Instant accessExpiry, Instant revokedAt, int idleTimeoutSeconds, String passwordHash) {
        boolean isLive() {
            Instant now = Instant.now();
            return revokedAt == null && now.isBefore(accessExpiry) && now.isBefore(absoluteExpiry)
                    && now.isBefore(lastActivity.plusSeconds(idleTimeoutSeconds));
        }
    }
}
