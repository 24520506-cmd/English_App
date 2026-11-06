package com.englishapp.server.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

@Component
public class ApiRequestLoggingFilter extends OncePerRequestFilter {
    static final String REQUEST_ID_ATTRIBUTE = ApiRequestLoggingFilter.class.getName() + ".requestId";
    static final String AUTHENTICATED_USER_ID_ATTRIBUTE = ApiRequestLoggingFilter.class.getName() + ".userId";
    static final String ERROR_CODE_ATTRIBUTE = ApiRequestLoggingFilter.class.getName() + ".errorCode";

    private static final Logger log = LoggerFactory.getLogger(ApiRequestLoggingFilter.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int MAX_BODY_BYTES = 4096;
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
            "password", "currentpassword", "newpassword", "passwordhash", "accesstoken", "refreshtoken",
            "token", "otp", "otphash", "code", "verificationcode", "authorization", "cookie", "secret",
            "email", "phone", "contact", "newvalue",
            "clientsecret", "idtoken", "googletoken", "resetgrant", "grant", "state", "nonce",
            "authorizationurl", "codechallenge", "codeverifier");

    private final boolean includeBodies;

    public ApiRequestLoggingFilter(@Value("${app.api-logging.include-bodies:false}") boolean includeBodies) {
        this.includeBodies = includeBodies;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/api/v1") && !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = requestId(request);
        response.setHeader("X-Request-Id", requestId);
        long startedAt = System.nanoTime();
        boolean escapedWithError = false;
        ContentCachingRequestWrapper cachedRequest = includeBodies
                ? new ContentCachingRequestWrapper(request, MAX_BODY_BYTES) : null;
        ContentCachingResponseWrapper cachedResponse = includeBodies
                ? new ContentCachingResponseWrapper(response) : null;
        HttpServletRequest requestToUse = cachedRequest == null ? request : cachedRequest;
        HttpServletResponse responseToUse = cachedResponse == null ? response : cachedResponse;
        try {
            chain.doFilter(requestToUse, responseToUse);
        } catch (IOException | ServletException | RuntimeException failure) {
            escapedWithError = true;
            if (responseToUse.getStatus() < HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
                responseToUse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            throw failure;
        } finally {
            long durationMillis = (System.nanoTime() - startedAt) / 1_000_000;
            int status = responseToUse.getStatus();
            String route = route(request);
            Object userId = request.getAttribute(AUTHENTICATED_USER_ID_ATTRIBUTE);
            String outcome = escapedWithError ? "exception" : outcome(status);
            Object errorCode = request.getAttribute(ERROR_CODE_ATTRIBUTE);
            String requestBody = cachedRequest == null ? "-" : bodyForLog(request.getContentType(),
                    cachedRequest.getContentAsByteArray());
            String responseBody = cachedResponse == null ? "-" : bodyForLog(responseToUse.getContentType(),
                    cachedResponse.getContentAsByteArray());
            try {
                String line = "API_REQUEST request_id={} method={} route={} status={} outcome={} error_code={} duration_ms={} user_id={} remote_ip={} request_body={} response_body={}";
                Object[] fields = {requestId, safe(request.getMethod()), safe(route), status, outcome,
                        errorCode == null ? "-" : safe(errorCode.toString()), durationMillis,
                        userId == null ? "-" : safe(userId.toString()), safe(request.getRemoteAddr()),
                        requestBody, responseBody};

                if (status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR || escapedWithError) {
                    log.error(line, fields);
                } else if (status >= HttpServletResponse.SC_BAD_REQUEST) {
                    log.warn(line, fields);
                } else {
                    log.info(line, fields);
                }
            } finally {
                if (cachedResponse != null) cachedResponse.copyBodyToResponse();
            }
        }
    }

    static String requestId(HttpServletRequest request) {
        Object existing = request.getAttribute(REQUEST_ID_ATTRIBUTE);
        if (existing instanceof String value) return value;
        String generated = "req_" + UUID.randomUUID().toString().replace("-", "");
        request.setAttribute(REQUEST_ID_ATTRIBUTE, generated);
        return generated;
    }

    private static String route(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern instanceof String value ? value : request.getRequestURI();
    }

    private static String outcome(int status) {
        if (status >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) return "server_error";
        if (status >= HttpServletResponse.SC_BAD_REQUEST) return "client_error";
        return "success";
    }

    private static String bodyForLog(String contentType, byte[] body) {
        if (body.length == 0) return "-";
        if (body.length >= MAX_BODY_BYTES) return "[omitted: body exceeds 4096 bytes]";
        if (contentType == null || !(contentType.contains("application/json") || contentType.contains("+json"))) {
            return "[omitted: non-JSON body]";
        }
        try {
            JsonNode json = JSON.readTree(body);
            redact(json);
            return JSON.writeValueAsString(json);
        } catch (Exception invalidJson) {
            return "[omitted: invalid JSON body]";
        }
    }

    private static void redact(JsonNode node) {
        if (node instanceof ObjectNode object) {
            for (var property : object.properties()) {
                String normalizedKey = property.getKey().replaceAll("[^A-Za-z0-9]", "")
                        .toLowerCase(Locale.ROOT);
                if (SENSITIVE_FIELDS.contains(normalizedKey)) {
                    object.put(property.getKey(), "[REDACTED]");
                } else {
                    redact(property.getValue());
                }
            }
        } else if (node instanceof ArrayNode array) {
            for (JsonNode child : array) redact(child);
        }
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return "-";
        return value.replaceAll("[\\p{Cntrl}\\s]", "_");
    }
}
