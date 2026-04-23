package ru.ticketswap.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ru.ticketswap.auth.dto.ForgotPasswordRequest;
import ru.ticketswap.auth.dto.ForgotPasswordResponse;
import ru.ticketswap.auth.dto.LoginRequest;
import ru.ticketswap.auth.dto.LoginResponse;
import ru.ticketswap.auth.dto.ResetPasswordRequest;
import ru.ticketswap.auth.dto.ResetPasswordResponse;
import ru.ticketswap.auth.dto.TwoFactorResendRequest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @InjectMocks
    private AuthController authController;

    @Test
    void loginDelegatesToAuthService() {
        LoginRequest request = new LoginRequest("  user@example.com  ", "password123");
        LoginResponse loginResponse = LoginResponse.authenticated("jwt-token");

        when(authService.login(request)).thenReturn(loginResponse);

        ResponseEntity<LoginResponse> response = authController.login(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(loginResponse, response.getBody());
        verify(authService).login(request);
    }

    @Test
    void resendTwoFactorDelegatesToAuthService() {
        TwoFactorResendRequest request = new TwoFactorResendRequest("challenge-123");
        LoginResponse loginResponse = LoginResponse.twoFactorRequired(
                "challenge-456",
                Instant.parse("2026-04-23T12:00:00Z")
        );

        when(authService.resendTwoFactor(request)).thenReturn(loginResponse);

        ResponseEntity<LoginResponse> response = authController.resendTwoFactor(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(loginResponse, response.getBody());
        verify(authService).resendTwoFactor(request);
    }

    @Test
    void forgotPasswordDelegatesToPasswordResetService() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("user@example.com");

        ResponseEntity<ForgotPasswordResponse> response = authController.forgotPassword(request);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals(
                "If an account with that email exists, password reset instructions have been sent",
                response.getBody().message()
        );
        verify(passwordResetService).requestPasswordReset("user@example.com");
    }

    @Test
    void resetPasswordDelegatesToPasswordResetService() {
        ResetPasswordRequest request = new ResetPasswordRequest("token-123", "new-password");

        ResponseEntity<ResetPasswordResponse> response = authController.resetPassword(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Password successfully reset", response.getBody().message());
        verify(passwordResetService).resetPassword("token-123", "new-password");
    }
}