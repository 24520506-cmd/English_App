package com.englishapp.server.auth;

import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiFailureHandlerTest {
    @Test void errorEnvelopeIncludesSupportRequestIdWithoutSecrets() {
        Map<String, Object> body = new ApiFailureHandler().handle(ApiFailure.invalidCredentials()).getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        assertEquals("INVALID_CREDENTIALS", error.get("code"));
        assertNotNull(error.get("request_id"));
        assertTrue(((String) error.get("request_id")).matches("req_[0-9a-f]{32}"));
    }
}
