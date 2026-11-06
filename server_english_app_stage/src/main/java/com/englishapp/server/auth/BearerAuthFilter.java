package com.englishapp.server.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class BearerAuthFilter extends OncePerRequestFilter {
    private final AuthService auth;

    public BearerAuthFilter(AuthService auth) { this.auth = auth; }

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!"POST".equals(request.getMethod())) return false;
        return path.equals("/api/v1/users") || path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/refresh");
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            reject(request, response, "SESSION_INVALID", "Phiên không hợp lệ. Vui lòng đăng nhập lại.");
            return;
        }
        try {
            AuthService.User user = auth.authenticate(header.substring(7));
            request.setAttribute(ApiRequestLoggingFilter.AUTHENTICATED_USER_ID_ATTRIBUTE, user.id().toString());
            var principal = new UsernamePasswordAuthenticationToken(user.id().toString(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role().toUpperCase(Locale.ROOT))));
            SecurityContextHolder.getContext().setAuthentication(principal);
            chain.doFilter(request, response);
        } catch (ApiFailure failure) {
            reject(request, response, failure.code(), failure.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, String code, String message)
            throws IOException {
        String requestId = ApiRequestLoggingFilter.requestId(request);
        request.setAttribute(ApiRequestLoggingFilter.ERROR_CODE_ATTRIBUTE, code);
        response.setStatus(401);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Request-Id", requestId);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":{\"code\":\"" + code + "\",\"message\":\""
                + message + "\",\"request_id\":\"" + requestId + "\"}}");
    }
}
