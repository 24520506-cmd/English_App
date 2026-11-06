package com.englishapp.server.auth;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class OtpProof {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] secret;

    OtpProof(@Value("${app.otp.hmac-secret:}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    String newCode() {
        return "%06d".formatted(RANDOM.nextInt(1_000_000));
    }

    String hash(String challengeId, String code) {
        if (secret.length < 32)
            throw new IllegalStateException("OTP_HMAC_SECRET must contain at least 32 bytes");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((challengeId + ":" + code)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
