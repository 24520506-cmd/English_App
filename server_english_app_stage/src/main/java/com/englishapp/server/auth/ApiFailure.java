package com.englishapp.server.auth;

import org.springframework.http.HttpStatus;

public class ApiFailure extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public ApiFailure(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public HttpStatus status() { return status; }

    public static ApiFailure invalidCredentials() {
        return new ApiFailure("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED,
                "Thông tin đăng nhập không hợp lệ");
    }
}
