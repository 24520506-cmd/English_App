package com.englishapp.server.auth;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.*;

class ApiFailureHandlerTest {
    @Test void errorEnvelopeIncludesSupportRequestIdWithoutSecrets() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        Map<String, Object> body = new ApiFailureHandler()
                .handle(ApiFailure.invalidCredentials(), request).getBody();
        assertNotNull(body);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        assertEquals("INVALID_CREDENTIALS", error.get("code"));
        assertNotNull(error.get("request_id"));
        assertTrue(((String) error.get("request_id")).matches("req_[0-9a-f]{32}"));
    }

    @Test void errorEnvelopeUsesRequestIdWrittenToRuntimeLog() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        String requestId = "req_0123456789abcdef0123456789abcdef";
        request.setAttribute(ApiRequestLoggingFilter.REQUEST_ID_ATTRIBUTE, requestId);

        Map<String, Object> body = new ApiFailureHandler()
                .handle(ApiFailure.invalidCredentials(), request).getBody();

        assertNotNull(body);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) body.get("error");
        assertEquals(requestId, error.get("request_id"));
    }
}
