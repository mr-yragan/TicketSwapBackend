package ru.ticketswap.auth;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.ticketswap.auth.dto.AuthRequest;
import ru.ticketswap.auth.dto.AuthResponse;
import ru.ticketswap.auth.dto.ForgotPasswordRequest;
import ru.ticketswap.auth.dto.ForgotPasswordResponse;
import ru.ticketswap.auth.dto.LoginRequest;
import ru.ticketswap.auth.dto.LoginResponse;
import ru.ticketswap.auth.dto.ResetPasswordRequest;
import ru.ticketswap.auth.dto.ResetPasswordResponse;
import ru.ticketswap.auth.dto.TwoFactorVerifyRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody AuthRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/2fa/verify")
    public ResponseEntity<AuthResponse> verifyTwoFactor(@Valid @RequestBody TwoFactorVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyTwoFactor(request));
    }

    @PostMapping("/password/forgot")
    public ResponseEntity<ForgotPasswordResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestPasswordReset(request.email());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new ForgotPasswordResponse(
                        "If an account with that email exists, password reset instructions have been sent"
                ));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<ResetPasswordResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(new ResetPasswordResponse("Password successfully reset"));
    }
}
