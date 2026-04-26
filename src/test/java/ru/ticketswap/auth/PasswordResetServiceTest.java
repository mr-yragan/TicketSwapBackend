package ru.ticketswap.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import ru.ticketswap.common.MailDeliveryException;
import ru.ticketswap.config.TicketSwapProperties;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserIdentityService;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Mock
    private UserIdentityService userIdentityService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private MailService mailService;

    @Mock
    private TwoFactorService twoFactorService;

    @Test
    void requestPasswordResetCreatesTokenAndSendsEmail() {
        PasswordResetService service = new PasswordResetService(
                passwordResetTokenRepository,
                userIdentityService,
                passwordEncoder,
                mailService,
                twoFactorService,
                properties(900_000)
        );
        User user = new User("user@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 42L);

        when(userIdentityService.normalizeEmail("User@Example.com")).thenReturn("user@example.com");
        when(userIdentityService.findUserByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.requestPasswordReset("User@Example.com");

        verify(passwordResetTokenRepository).save(any(PasswordResetToken.class));
        verify(mailService).sendPasswordResetLink(eq("user@example.com"), anyString(), any(Instant.class));
        verify(passwordResetTokenRepository).invalidateActiveTokensExcept(eq(42L), anyString(), any(Instant.class));
    }

    @Test
    void requestPasswordResetDoesNothingForUnknownEmail() {
        PasswordResetService service = new PasswordResetService(
                passwordResetTokenRepository,
                userIdentityService,
                passwordEncoder,
                mailService,
                twoFactorService,
                properties(900_000)
        );

        when(userIdentityService.normalizeEmail("missing@example.com")).thenReturn("missing@example.com");
        when(userIdentityService.findUserByEmail("missing@example.com")).thenReturn(Optional.empty());

        service.requestPasswordReset("missing@example.com");

        verifyNoInteractions(passwordResetTokenRepository, mailService, passwordEncoder, twoFactorService);
    }

    @Test
    void requestPasswordResetConsumesNewTokenWhenMailFails() {
        PasswordResetService service = new PasswordResetService(
                passwordResetTokenRepository,
                userIdentityService,
                passwordEncoder,
                mailService,
                twoFactorService,
                properties(900_000)
        );
        User user = new User("user@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 42L);
        ArgumentCaptor<PasswordResetToken> tokenCaptor = ArgumentCaptor.forClass(PasswordResetToken.class);

        when(userIdentityService.normalizeEmail("user@example.com")).thenReturn("user@example.com");
        when(userIdentityService.findUserByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new MailDeliveryException("Ошибка отправки письма", new RuntimeException("Внутренняя ошибка")))
                .when(mailService)
                .sendPasswordResetLink(eq("user@example.com"), anyString(), any(Instant.class));

        service.requestPasswordReset("user@example.com");

        verify(passwordResetTokenRepository).save(tokenCaptor.capture());
        assertTrue(tokenCaptor.getValue().isConsumed());
        verify(passwordResetTokenRepository, never()).invalidateActiveTokensExcept(eq(42L), anyString(), any(Instant.class));
    }

    @Test
    void resetPasswordUpdatesPasswordAndInvalidatesOldTokens() {
        PasswordResetService service = new PasswordResetService(
                passwordResetTokenRepository,
                userIdentityService,
                passwordEncoder,
                mailService,
                twoFactorService,
                properties(900_000)
        );
        User user = new User("user@example.com", "old-hash");
        ReflectionTestUtils.setField(user, "id", 42L);
        PasswordResetToken token = new PasswordResetToken(user, "hashed-token", Instant.now().plusSeconds(60));

        when(passwordResetTokenRepository.findForUpdateByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode("new-password-123")).thenReturn("new-hash");

        service.resetPassword("token-value", "new-password-123");

        assertEquals("new-hash", user.getPasswordHash());
        assertTrue(token.isConsumed());
        verify(passwordResetTokenRepository).invalidateActiveTokens(eq(42L), any(Instant.class));
        verify(twoFactorService).invalidateChallengesForUser(42L);
    }

    @Test
    void resetPasswordRejectsExpiredToken() {
        PasswordResetService service = new PasswordResetService(
                passwordResetTokenRepository,
                userIdentityService,
                passwordEncoder,
                mailService,
                twoFactorService,
                properties(900_000)
        );
        User user = new User("user@example.com", "old-hash");
        ReflectionTestUtils.setField(user, "id", 42L);
        PasswordResetToken token = new PasswordResetToken(user, "hashed-token", Instant.now().minusSeconds(1));

        when(passwordResetTokenRepository.findForUpdateByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThrows(ExpiredPasswordResetTokenException.class, () -> service.resetPassword("token-value", "new-password-123"));
        assertTrue(token.isConsumed());
    }

    private TicketSwapProperties properties(long tokenExpirationMs) {
        TicketSwapProperties properties = new TicketSwapProperties();
        properties.getSecurity().getPasswordReset().setTokenExpirationMs(tokenExpirationMs);
        return properties;
    }
}