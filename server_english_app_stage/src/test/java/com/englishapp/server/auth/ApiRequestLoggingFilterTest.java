package com.englishapp.server.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(OutputCaptureExtension.class)
class ApiRequestLoggingFilterTest {
    @Test void logsApiOutcomeWithCorrelatedIdAndNeverLogsSecrets(CapturedOutput output) throws Exception {
        ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
        request.setQueryString("access_token=query-secret");
        request.addHeader("Authorization", "Bearer header-secret");
        request.setAttribute(ApiRequestLoggingFilter.AUTHENTICATED_USER_ID_ATTRIBUTE, "user-123");
        request.setAttribute(ApiRequestLoggingFilter.ERROR_CODE_ATTRIBUTE, "SESSION_INVALID");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_UNAUTHORIZED));

        String requestId = response.getHeader("X-Request-Id");
        assertNotNull(requestId);
        assertTrue(requestId.matches("req_[0-9a-f]{32}"));
        assertEquals(requestId, request.getAttribute(ApiRequestLoggingFilter.REQUEST_ID_ATTRIBUTE));
        String logged = output.toString();
        assertTrue(logged.contains("API_REQUEST"));
        assertTrue(logged.contains("request_id=" + requestId));
        assertTrue(logged.contains("method=GET"));
        assertTrue(logged.contains("route=/api/v1/me"));
        assertTrue(logged.contains("status=401"));
        assertTrue(logged.contains("error_code=SESSION_INVALID"));
        assertTrue(logged.contains("user_id=user-123"));
        assertFalse(logged.contains("query-secret"));
        assertFalse(logged.contains("header-secret"));
    }

    @Test void ignoresRequestsOutsideTheApi() throws Exception {
        ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/favicon.ico");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

        assertNull(response.getHeader("X-Request-Id"));
    }

    @Test void logsJsonBodiesWithSecretsRedactedAndKeepsResponseIntact(CapturedOutput output) throws Exception {
        ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter(true);
        String requestBody = "{\"username\":\"learner\",\"password\":\"pass-secret\","
                + "\"email\":\"learner@example.com\",\"otp\":\"123456\"}";
        String responseBody = "{\"access_token\":\"access-secret\",\"user\":{\"username\":\"learner\"}}";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/users");
        request.setContentType("application/json");
        request.setContent(requestBody.getBytes(StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            servletRequest.getInputStream().readAllBytes();
            servletResponse.setContentType("application/json");
            servletResponse.getWriter().write(responseBody);
        });

        String logged = output.toString();
        assertTrue(logged.contains("request_body={\"username\":\"learner\""));
        assertTrue(logged.contains("\"password\":\"[REDACTED]"));
        assertTrue(logged.contains("\"otp\":\"[REDACTED]"));
        assertTrue(logged.contains("response_body={\"access_token\":\"[REDACTED]"));
        assertFalse(logged.contains("pass-secret"));
        assertFalse(logged.contains("123456"));
        assertFalse(logged.contains("access-secret"));
        assertEquals(responseBody, response.getContentAsString());
    }
}
