package com.englishapp.server.auth;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiFailureHandler {
    @ExceptionHandler(ApiFailure.class)
    ResponseEntity<Map<String, Object>> handle(ApiFailure failure) {
        return ResponseEntity.status(failure.status())
                .body(Map.of("error", Map.of("code", failure.code(), "message", failure.getMessage(),
                        "request_id", requestId())));
    }

    static String requestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "");
    }
}
