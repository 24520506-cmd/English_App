package com.englishapp.server.auth;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final PasswordRecoveryService passwordRecovery;

    public AuthController(AuthService auth, PasswordRecoveryService passwordRecovery) {
        this.auth = auth;
        this.passwordRecovery = passwordRecovery;
    }

    public record LoginRequest(String username, String password, String device_label, String platform) { }
    public record RefreshRequest(String refresh_token) { }
    public record RecoveryStartRequest(String channel, String login_identifier, String contact) { }
    public record RecoveryVerifyRequest(String otp) { }
    public record RecoveryCompleteRequest(String reset_grant, String new_password) { }

    @Operation(security = {})
    @PostMapping("/login")
    ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request, HttpServletRequest http) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(auth.login(request.username(), request.password(), request.device_label(), request.platform(),
                        http.getRemoteAddr()));
    }

    @Operation(security = {})
    @PostMapping("/refresh")
    ResponseEntity<Map<String, Object>> refresh(@RequestBody RefreshRequest request) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(auth.refresh(request.refresh_token()));
    }

    @Operation(security = {})
    @PostMapping("/password-reset/challenges")
    ResponseEntity<Map<String, Object>> startPasswordReset(@RequestBody RecoveryStartRequest request,
            HttpServletRequest http) {
        return ResponseEntity.accepted().body(passwordRecovery.start(request.channel(),
                request.login_identifier(), request.contact(), http.getRemoteAddr()));
    }

    @Operation(security = {})
    @PostMapping("/password-reset/challenges/{challengeId}/resend")
    ResponseEntity<Map<String, Object>> resendPasswordReset(@PathVariable UUID challengeId,
            HttpServletRequest http) {
        return ResponseEntity.accepted().body(passwordRecovery.resend(challengeId, http.getRemoteAddr()));
    }

    @Operation(security = {})
    @PostMapping("/password-reset/challenges/{challengeId}/verify")
    Map<String, Object> verifyPasswordReset(@PathVariable UUID challengeId,
            @RequestBody RecoveryVerifyRequest request) {
        return passwordRecovery.verify(challengeId, request.otp());
    }

    @Operation(security = {})
    @PostMapping("/password-reset/complete")
    ResponseEntity<Void> completePasswordReset(@RequestBody RecoveryCompleteRequest request) {
        passwordRecovery.complete(request.reset_grant(), request.new_password());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@RequestHeader("Authorization") String header) {
        auth.logout(BearerToken.extract(header));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/activity")
    ResponseEntity<Void> activity(@RequestHeader("Authorization") String header) {
        auth.activity(BearerToken.extract(header));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session")
    Map<String, Object> session(@RequestHeader("Authorization") String header) {
        return auth.session(BearerToken.extract(header));
    }
}
