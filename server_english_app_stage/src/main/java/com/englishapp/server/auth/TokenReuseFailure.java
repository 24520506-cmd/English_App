package com.englishapp.server.auth;

import org.springframework.http.HttpStatus;

public final class TokenReuseFailure extends ApiFailure {
    public TokenReuseFailure() {
        super("REFRESH_REUSE_DETECTED", HttpStatus.UNAUTHORIZED,
                "Phiên đã bị thu hồi vì lý do bảo mật. Vui lòng đăng nhập lại.");
    }
}
