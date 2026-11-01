package com.englishapp.server.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TokenCodecTest {
    @Test
    void randomTokensAreDistinctAndHashesAreStable() {
        String first = TokenCodec.newToken();
        String second = TokenCodec.newToken();
        assertNotEquals(first, second);
        assertTrue(first.length() >= 40);
        assertEquals(TokenCodec.sha256(first), TokenCodec.sha256(first));
        assertNotEquals(first, TokenCodec.sha256(first));
    }
}
