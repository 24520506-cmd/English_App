package com.englishapp.server.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
public class UserController {
    private final AuthService auth;
    private final PhoneVerificationService phoneVerification;
    private final EmailVerificationService emailVerification;
    private final ContactChangeService contactChange;
    private final GoogleLinkService googleLink;

    public UserController(AuthService auth, PhoneVerificationService phoneVerification,
            EmailVerificationService emailVerification, ContactChangeService contactChange,
            GoogleLinkService googleLink) {
        this.auth = auth;
        this.phoneVerification = phoneVerification;
        this.emailVerification = emailVerification;
        this.contactChange = contactChange;
        this.googleLink = googleLink;
    }

    public record RegisterRequest(String username, String password, String email, String phone) { }
    public record PasswordChangeRequest(String current_password, String new_password) { }
    public record PreferencesRequest(String band, List<String> topic_ids) { }
    public record OtpVerifyRequest(String code) { }
    public record ContactChangeRequest(String kind, String new_value, String current_password) { }
    public record ContactChangeVerifyRequest(String otp) { }
    public record GoogleLinkStartRequest(String current_password, String change_id) { }

    @Operation(security = {})
    @PostMapping("/users")
    ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(auth.register(
                request.username(), request.password(), request.email(), request.phone()));
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
        if (!learner) throw new ApiFailure("FORBIDDEN", HttpStatus.FORBIDDEN,
                "Không có quyền thực hiện thao tác này.");
        return UUID.fromString(principal.getName());
    }

    @PostMapping("/me/contact-changes")
    ResponseEntity<Map<String, Object>> startContactChange(@RequestHeader("Authorization") String header,
            @RequestBody ContactChangeRequest request, HttpServletRequest http) {
        return ResponseEntity.accepted().body(contactChange.start(BearerToken.extract(header), request.kind(),
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
            try {
                changeId = UUID.fromString(request.change_id());
            } catch (IllegalArgumentException invalid) {
                throw new ApiFailure("VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                        "Vui lòng kiểm tra thông tin đã nhập.");
            }
        }
        return googleLink.start(BearerToken.extract(header), request.current_password(), changeId);
    }

    @Operation(security = {})
    @GetMapping("/auth/google-link/callback")
    ResponseEntity<Void> googleLinkCallback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state, @RequestParam(required = false) String error) {
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(googleLink.callback(code, state, error)))
                .build();
    }

    @PostMapping("/me/password-change")
    ResponseEntity<Void> changePassword(@RequestHeader("Authorization") String header,
            @RequestBody PasswordChangeRequest request) {
        auth.changePassword(BearerToken.extract(header), request.current_password(), request.new_password());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me/sessions")
    Map<String, Object> sessions(@RequestHeader("Authorization") String header) {
        return auth.sessions(BearerToken.extract(header));
    }

    @DeleteMapping("/me/sessions/{sessionId}")
    ResponseEntity<Void> revokeSession(@RequestHeader("Authorization") String header,
            @PathVariable String sessionId) {
        auth.revokeSession(BearerToken.extract(header), sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/sessions/revoke-all")
    ResponseEntity<Void> revokeAllSessions(@RequestHeader("Authorization") String header) {
        auth.revokeAllSessions(BearerToken.extract(header));
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
