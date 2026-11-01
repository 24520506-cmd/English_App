package com.englishapp.server.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OtpProofTest {
    private final OtpProof proof = new OtpProof("0123456789abcdef0123456789abcdef");

    @Test void createsSixDigitCodes() {
        assertTrue(proof.newCode().matches("[0-9]{6}"));
    }

    @Test void proofIsBoundToChallengeAndCode() {
        String value = proof.hash("challenge-a", "123456");
        assertEquals(64, value.length());
        assertEquals(value, proof.hash("challenge-a", "123456"));
        assertNotEquals(value, proof.hash("challenge-b", "123456"));
        assertNotEquals(value, proof.hash("challenge-a", "654321"));
    }

    @Test void vietnamesePhonesAreNormalizedToE164() {
        assertEquals("+84325569553", AuthService.normalizePhone("0325569553"));
        assertEquals("+84325569553", AuthService.normalizePhone("84325569553"));
        assertEquals("+84325569553", AuthService.normalizePhone("+84325569553"));
    }
}
