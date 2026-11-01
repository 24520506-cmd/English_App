package com.englishapp.server.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    public record RegisterRequest(String username, String password, String email, String phone) { }
    public record LoginRequest(String username, String password, String device_label, String platform) { }
    public record RefreshRequest(String refresh_token) { }
    public record PasswordChangeRequest(String current_password, String new_password) { }

    @PostMapping("/users")
    ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(
                request.username(), request.password(), request.email(), request.phone()));
    }

    @PostMapping("/auth/login")
    ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request, HttpServletRequest http) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(auth.login(request.username(), request.password(), request.device_label(), request.platform(),
                        http.getRemoteAddr()));
    }

    @PostMapping("/auth/refresh")
    ResponseEntity<Map<String, Object>> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(auth.refresh(request.refresh_token()));
    }

    @GetMapping("/me")
    Map<String, Object> profile(Authentication principal) {
        return auth.profile(UUID.fromString(principal.getName()));
    }

    @PatchMapping("/me")
    Map<String, Object> updateProfile(Authentication principal, @RequestBody Map<String, Object> changes) {
        return auth.updateProfile(UUID.fromString(principal.getName()), changes);
    }

    @PostMapping("/me/password-change")
    ResponseEntity<Void> changePassword(@RequestHeader("Authorization") String header,
                                        @RequestBody PasswordChangeRequest request) {
        auth.changePassword(header.substring(7), request.current_password(), request.new_password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/sessions")
    Map<String, Object> sessions(@RequestHeader("Authorization") String header) {
        return auth.sessions(header.substring(7));
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    ResponseEntity<Void> revokeSession(@RequestHeader("Authorization") String header,
                                       @PathVariable String sessionId) {
        auth.revokeSession(header.substring(7), sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/sessions/revoke-all")
    ResponseEntity<Void> revokeAllSessions(@RequestHeader("Authorization") String header) {
        auth.revokeAllSessions(header.substring(7));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/auth/session")
    Map<String, Object> session(@RequestHeader("Authorization") String header) {
        return auth.session(header.substring(7));
    }

    @PostMapping("/auth/logout")
    ResponseEntity<Void> logout(@RequestHeader("Authorization") String header) {
        auth.logout(header.substring(7));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/activity")
    ResponseEntity<Void> activity(@RequestHeader("Authorization") String header) {
        auth.activity(header.substring(7));
        return ResponseEntity.noContent().build();
    }
}
