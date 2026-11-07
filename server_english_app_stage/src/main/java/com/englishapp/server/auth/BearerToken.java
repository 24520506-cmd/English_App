package com.englishapp.server.auth;

import org.springframework.http.HttpStatus;

final class BearerToken {
    private BearerToken() { }

    static String extract(String header) {
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7)
            throw new ApiFailure("SESSION_INVALID", HttpStatus.UNAUTHORIZED,
                    "Phiên không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.");
        return header.substring(7);
    }
}
