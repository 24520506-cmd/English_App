package com.englishapp.server.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService auth;
    private final PhoneVerificationService phoneVerification;
    private final EmailVerificationService emailVerification;
    private final PasswordRecoveryService passwordRecovery;
    private final ContactChangeService contactChange;
    private final GoogleLinkService googleLink;

    public AuthController(AuthService auth, PhoneVerificationService phoneVerification,
            EmailVerificationService emailVerification, PasswordRecoveryService passwordRecovery,
            ContactChangeService contactChange, GoogleLinkService googleLink) {
        this.auth = auth;
        this.phoneVerification = phoneVerification;
        this.emailVerification = emailVerification;
        this.passwordRecovery = passwordRecovery;
        this.contactChange = contactChange;
        this.googleLink = googleLink;
    }

    public record RegisterRequest(String username, String password, String email, String phone) { }
    public record LoginRequest(String username, String password, String device_label, String platform) { }
    public record RefreshRequest(String refresh_token) { }
    public record PasswordChangeRequest(String current_password, String new_password) { }
    public record PreferencesRequest(String band, List<String> topic_ids) { }
    public record OtpVerifyRequest(String code) { }
    public record RecoveryStartRequest(String channel, String login_identifier, String contact) { }
    public record RecoveryVerifyRequest(String otp) { }
    public record RecoveryCompleteRequest(String reset_grant, String new_password) { }
    public record ContactChangeRequest(String kind, String new_value, String current_password) { }
    public record ContactChangeVerifyRequest(String otp) { }
    public record GoogleLinkStartRequest(String current_password, String change_id) { }

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

    @PostMapping("/auth/password-reset/challenges")
    ResponseEntity<Map<String, Object>> startPasswordReset(@RequestBody RecoveryStartRequest request,
            HttpServletRequest http) {
        return ResponseEntity.accepted().body(passwordRecovery.start(request.channel(),
                request.login_identifier(), request.contact(), http.getRemoteAddr()));
    }

    @PostMapping("/auth/password-reset/challenges/{challengeId}/resend")
    ResponseEntity<Map<String, Object>> resendPasswordReset(@PathVariable UUID challengeId,
            HttpServletRequest http) {
        return ResponseEntity.accepted().body(passwordRecovery.resend(challengeId, http.getRemoteAddr()));
    }

    @PostMapping("/auth/password-reset/challenges/{challengeId}/verify")
    Map<String, Object> verifyPasswordReset(@PathVariable UUID challengeId,
            @RequestBody RecoveryVerifyRequest request) {
        return passwordRecovery.verify(challengeId, request.otp());
    }

    @PostMapping("/auth/password-reset/complete")
    ResponseEntity<Void> completePasswordReset(@RequestBody RecoveryCompleteRequest request) {
        passwordRecovery.complete(request.reset_grant(), request.new_password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    Map<String, Object> profile(Authentication principal) {
        return auth.profile(UUID.fromString(principal.getName()));
    }

    @PatchMapping("/me")
    Map<String, Object> updateProfile(Authentication principal, @RequestBody Map<String, Object> changes) {
        return auth.updateProfile(UUID.fromString(principal.getName()), changes);
    }

    @GetMapping("/me/preferences")
    Map<String, Object> preferences(Authentication principal) {
        return auth.preferences(learnerId(principal));
    }

    @PutMapping("/me/preferences")
    Map<String, Object> updatePreferences(Authentication principal, @RequestBody PreferencesRequest request) {
        return auth.updatePreferences(learnerId(principal), request.band(), request.topic_ids());
    }

    private static UUID learnerId(Authentication principal) {
        boolean learner = principal != null && principal.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_LEARNER".equals(authority.getAuthority()));
        if (!learner) throw new ApiFailure("FORBIDDEN", HttpStatus.FORBIDDEN, "Không có quyền thực hiện thao tác này.");
        return UUID.fromString(principal.getName());
    }

    @PostMapping("/me/contact-changes")
    ResponseEntity<Map<String, Object>> startContactChange(@RequestHeader("Authorization") String header,
            @RequestBody ContactChangeRequest request, HttpServletRequest http) {
        return ResponseEntity.accepted().body(contactChange.start(bearerToken(header), request.kind(),
                request.new_value(), request.current_password(), http.getRemoteAddr()));
    }

    @PostMapping("/me/contact-changes/{changeId}/verify")
    Map<String, Object> verifyContactChange(Authentication principal, @PathVariable UUID changeId,
            @RequestBody ContactChangeVerifyRequest request) {
        return contactChange.verify(UUID.fromString(principal.getName()), changeId, request.otp());
    }

    @PostMapping("/me/google-link/start")
    Map<String, Object> startGoogleLink(@RequestHeader("Authorization") String header,
            @RequestBody GoogleLinkStartRequest request) {
        UUID changeId = null;
        if (request.change_id() != null && !request.change_id().isBlank()) {
            try { changeId = UUID.fromString(request.change_id()); }
            catch (IllegalArgumentException invalid) {
                throw new ApiFailure("VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                        "Vui lòng kiểm tra thông tin đã nhập.");
            }
        }
        return googleLink.start(bearerToken(header), request.current_password(), changeId);
    }

    @GetMapping("/auth/google-link/callback")
    ResponseEntity<Void> googleLinkCallback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state, @RequestParam(required = false) String error) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(googleLink.callback(code, state, error)))
                .build();
    }

    private static String bearerToken(String header) {
        if (header == null || !header.startsWith("Bearer ") || header.length() <= 7)
            throw new ApiFailure("SESSION_INVALID", HttpStatus.UNAUTHORIZED,
                    "Phiên không hợp lệ hoặc đã hết hạn. Vui lòng đăng nhập lại.");
        return header.substring(7);
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

    @PostMapping("/me/phone-verifications")
    ResponseEntity<Map<String, Object>> startPhoneVerification(Authentication principal,
            HttpServletRequest http) {
        return ResponseEntity.accepted().body(phoneVerification.create(
                UUID.fromString(principal.getName()), http.getRemoteAddr()));
    }

    @PostMapping("/me/phone-verifications/{challengeId}/resend")
    ResponseEntity<Map<String, Object>> resendPhoneVerification(Authentication principal,
            @PathVariable UUID challengeId, HttpServletRequest http) {
        return ResponseEntity.accepted().body(phoneVerification.resend(
                UUID.fromString(principal.getName()), challengeId, http.getRemoteAddr()));
    }

    @PostMapping("/me/phone-verifications/{challengeId}/verify")
    Map<String, Object> verifyPhone(Authentication principal, @PathVariable UUID challengeId,
            @RequestBody OtpVerifyRequest request) {
        return phoneVerification.verify(UUID.fromString(principal.getName()), challengeId, request.code());
    }

    @PostMapping("/me/email-verifications")
    ResponseEntity<Map<String, Object>> startEmailVerification(Authentication principal,
            HttpServletRequest http) {
        return ResponseEntity.accepted().body(emailVerification.create(
                UUID.fromString(principal.getName()), http.getRemoteAddr()));
    }

    @PostMapping("/me/email-verifications/{challengeId}/resend")
    ResponseEntity<Map<String, Object>> resendEmailVerification(Authentication principal,
            @PathVariable UUID challengeId, HttpServletRequest http) {
        return ResponseEntity.accepted().body(emailVerification.resend(
                UUID.fromString(principal.getName()), challengeId, http.getRemoteAddr()));
    }

    @PostMapping("/me/email-verifications/{challengeId}/verify")
    Map<String, Object> verifyEmail(Authentication principal, @PathVariable UUID challengeId,
            @RequestBody OtpVerifyRequest request) {
        return emailVerification.verify(UUID.fromString(principal.getName()), challengeId, request.code());
    }
}
