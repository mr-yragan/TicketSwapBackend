package ru.ticketswap.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.ticketswap.config.TicketSwapProperties;
import ru.ticketswap.user.User;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwoFactorServiceTest {

    @Mock
    private TwoFactorChallengeRepository challengeRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void verifyCodeReturnsUserAndConsumesChallenge() {
        TwoFactorService service = new TwoFactorService(challengeRepository, passwordEncoder, properties(300_000, 3));
        User user = new User("user@example.com", "hash");
        TwoFactorChallenge challenge = new TwoFactorChallenge(
                user,
                "challenge-123",
                "hashed-code",
                Instant.now().plusSeconds(60)
        );

        when(challengeRepository.findForUpdateByChallengeId("challenge-123")).thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("123456", "hashed-code")).thenReturn(true);

        User resolvedUser = service.verifyCode("challenge-123", "123456");

        assertSame(user, resolvedUser);
        assertTrue(challenge.isConsumed());
    }

    @Test
    void verifyCodeRejectsExpiredChallenges() {
        TwoFactorService service = new TwoFactorService(challengeRepository, passwordEncoder, properties(300_000, 3));
        User user = new User("user@example.com", "hash");
        TwoFactorChallenge challenge = new TwoFactorChallenge(
                user,
                "challenge-123",
                "hashed-code",
                Instant.now().minusSeconds(1)
        );

        when(challengeRepository.findForUpdateByChallengeId("challenge-123")).thenReturn(Optional.of(challenge));

        assertThrows(ExpiredTwoFactorCodeException.class, () -> service.verifyCode("challenge-123", "123456"));
        assertTrue(challenge.isConsumed());
    }

    @Test
    void verifyCodeConsumesChallengeAfterMaxAttempts() {
        TwoFactorService service = new TwoFactorService(challengeRepository, passwordEncoder, properties(300_000, 1));
        User user = new User("user@example.com", "hash");
        TwoFactorChallenge challenge = new TwoFactorChallenge(
                user,
                "challenge-123",
                "hashed-code",
                Instant.now().plusSeconds(60)
        );

        when(challengeRepository.findForUpdateByChallengeId("challenge-123")).thenReturn(Optional.of(challenge));
        when(passwordEncoder.matches("000000", "hashed-code")).thenReturn(false);

        assertThrows(InvalidTwoFactorCodeException.class, () -> service.verifyCode("challenge-123", "000000"));
        assertEquals(1, challenge.getFailedAttempts());
        assertTrue(challenge.isConsumed());
    }

    @Test
    void resendChallengeReturnsNewChallengeAndConsumesOldOne() {
        TwoFactorService service = new TwoFactorService(challengeRepository, passwordEncoder, properties(300_000, 3));
        User user = new User("user@example.com", "hash");
        TwoFactorChallenge currentChallenge = new TwoFactorChallenge(
                user,
                "challenge-123",
                "hashed-code",
                Instant.now().plusSeconds(60)
        );

        when(challengeRepository.findForUpdateByChallengeId("challenge-123")).thenReturn(Optional.of(currentChallenge));
        when(passwordEncoder.encode(any())).thenReturn("encoded-code");

        TwoFactorService.PendingTwoFactorChallenge resent = service.resendChallenge("challenge-123");

        assertTrue(currentChallenge.isConsumed());
        assertEquals("user@example.com", resent.email());
        assertEquals(36, resent.challengeId().length());
        assertEquals(6, resent.code().length());
        verify(challengeRepository).save(any(TwoFactorChallenge.class));
    }

    private TicketSwapProperties properties(long codeExpirationMs, int maxAttempts) {
        TicketSwapProperties properties = new TicketSwapProperties();
        properties.getSecurity().getTwoFactor().setCodeExpirationMs(codeExpirationMs);
        properties.getSecurity().getTwoFactor().setMaxAttempts(maxAttempts);
        return properties;
    }
}
