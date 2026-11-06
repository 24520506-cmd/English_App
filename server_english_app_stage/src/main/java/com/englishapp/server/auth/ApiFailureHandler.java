package com.englishapp.server.auth;

import java.util.Map;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiFailureHandler {
    @ExceptionHandler(ApiFailure.class)
    ResponseEntity<Map<String, Object>> handle(ApiFailure failure, HttpServletRequest request) {
        String requestId = ApiRequestLoggingFilter.requestId(request);
        request.setAttribute(ApiRequestLoggingFilter.ERROR_CODE_ATTRIBUTE, failure.code());
        return ResponseEntity.status(failure.status())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Request-Id")
                .header("X-Request-Id", requestId)
                .body(Map.of("error", Map.of("code", failure.code(), "message", failure.getMessage(),
                        "request_id", requestId)));
    }
}
