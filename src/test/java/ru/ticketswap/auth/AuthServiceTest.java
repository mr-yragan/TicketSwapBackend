package ru.ticketswap.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.ticketswap.auth.dto.AuthRequest;
import ru.ticketswap.auth.dto.AuthResponse;
import ru.ticketswap.auth.dto.LoginRequest;
import ru.ticketswap.auth.dto.LoginResponse;
import ru.ticketswap.auth.dto.TwoFactorResendRequest;
import ru.ticketswap.auth.dto.TwoFactorVerifyRequest;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserIdentityService;
import ru.ticketswap.user.UserRepository;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIdentityService userIdentityService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private TwoFactorService twoFactorService;

    @Mock
    private MailService mailService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private Authentication authentication;

    @Test
    void registerStoresEncodedPassword() {
        AuthService authService = new AuthService(
                userRepository,
                userIdentityService,
                passwordEncoder,
                authenticationManager,
                jwtService,
                twoFactorService,
                mailService,
                emailVerificationService
        );

        AuthRequest request = new AuthRequest("User@Example.com", "test_login", "password123");

        when(userIdentityService.normalizeEmail("User@Example.com")).thenReturn("user@example.com");
        when(userIdentityService.emailExists("user@example.com")).thenReturn(false);
        when(userIdentityService.normalizeLogin("test_login")).thenReturn("test_login");
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");

        authService.register(request);

        verify(userIdentityService).assertLoginAvailable("test_login", null);
        verify(userRepository).save(any(User.class));
        verify(emailVerificationService).createAndSendVerificationToken(any(User.class));
    }

    @Test
    void loginReturnsJwtWhenTwoFactorDisabled() {
        AuthService authService = new AuthService(
                userRepository,
                userIdentityService,
                passwordEncoder,
                authenticationManager,
                jwtService,
                twoFactorService,
                mailService,
                emailVerificationService
        );

        LoginRequest request = new LoginRequest("user@example.com", "password123");
        User user = new User("user@example.com", "hash");
        user.setEmailVerified(true);

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getName()).thenReturn("user@example.com");
        when(userIdentityService.findUserByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generate("user@example.com")).thenReturn("jwt-token");

        LoginResponse response = authService.login(request);

        assertFalse(response.requiresTwoFactor());
        assertEquals("jwt-token", response.token());
        assertNull(response.twoFactorChallengeId());
        verify(jwtService).generate("user@example.com");
    }

    @Test
    void loginReturnsChallengeWhenTwoFactorEnabled() {
        AuthService authService = new AuthService(
                userRepository,
                userIdentityService,
                passwordEncoder,
                authenticationManager,
                jwtService,
                twoFactorService,
                mailService,
                emailVerificationService
        );

        LoginRequest request = new LoginRequest("user@example.com", "password123");
        User user = new User("user@example.com", "hash");
        user.setEmailVerified(true);
        user.setTwoFactorEnabled(true);
        Instant expiresAt = Instant.parse("2026-03-30T12:00:00Z");

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(authentication.getName()).thenReturn("user@example.com");
        when(userIdentityService.findUserByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(twoFactorService.createChallenge(user))
                .thenReturn(new TwoFactorService.PendingTwoFactorChallenge(
                        "challenge-123",
                        "user@example.com",
                        "654321",
                        expiresAt
                ));

        LoginResponse response = authService.login(request);

        assertTrue(response.requiresTwoFactor());
        assertEquals("challenge-123", response.twoFactorChallengeId());
        assertEquals(expiresAt, response.twoFactorExpiresAt());
        assertNull(response.token());
        verify(mailService).sendTwoFactorCode("user@example.com", "654321", expiresAt);
    }

    @Test
    void verifyTwoFactorReturnsJwt() {
        AuthService authService = new AuthService(
                userRepository,
                userIdentityService,
                passwordEncoder,
                authenticationManager,
                jwtService,
                twoFactorService,
                mailService,
                emailVerificationService
        );

        User user = new User("user@example.com", "hash");

        when(twoFactorService.verifyCode("challenge-123", "123456")).thenReturn(user);
        when(jwtService.generate("user@example.com")).thenReturn("jwt-token");

        AuthResponse response = authService.verifyTwoFactor(new TwoFactorVerifyRequest("challenge-123", "123456"));

        assertEquals("jwt-token", response.token());
    }

    @Test
    void resendTwoFactorReturnsNewChallenge() {
        AuthService authService = new AuthService(
                userRepository,
                userIdentityService,
                passwordEncoder,
                authenticationManager,
                jwtService,
                twoFactorService,
                mailService,
                emailVerificationService
        );

        Instant expiresAt = Instant.parse("2026-04-23T12:00:00Z");
        when(twoFactorService.resendChallenge("challenge-123"))
                .thenReturn(new TwoFactorService.PendingTwoFactorChallenge(
                        "challenge-456",
                        "user@example.com",
                        "654321",
                        expiresAt
                ));

        LoginResponse response = authService.resendTwoFactor(new TwoFactorResendRequest("challenge-123"));

        assertTrue(response.requiresTwoFactor());
        assertEquals("challenge-456", response.twoFactorChallengeId());
        assertEquals(expiresAt, response.twoFactorExpiresAt());
        assertNull(response.token());
        verify(mailService).sendTwoFactorCode("user@example.com", "654321", expiresAt);
    }
}
