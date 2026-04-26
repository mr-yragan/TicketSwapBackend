package ru.ticketswap.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketswap.common.UnauthorizedException;
import ru.ticketswap.config.TicketSwapProperties;
import ru.ticketswap.user.User;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Service
public class TwoFactorService {

    private final TwoFactorChallengeRepository challengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final long codeExpirationMs;
    private final int maxAttempts;
    private final SecureRandom secureRandom = new SecureRandom();

    public TwoFactorService(
            TwoFactorChallengeRepository challengeRepository,
            PasswordEncoder passwordEncoder,
            TicketSwapProperties properties
    ) {
        this.challengeRepository = challengeRepository;
        this.passwordEncoder = passwordEncoder;
        this.codeExpirationMs = properties.getSecurity().getTwoFactor().getCodeExpirationMs();
        this.maxAttempts = properties.getSecurity().getTwoFactor().getMaxAttempts();
    }

    @Transactional
    public PendingTwoFactorChallenge createChallenge(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(codeExpirationMs);
        String code = generateCode();
        String challengeId = UUID.randomUUID().toString();

        challengeRepository.invalidateActiveChallenges(user.getId(), now);
        challengeRepository.save(new TwoFactorChallenge(
                user,
                challengeId,
                passwordEncoder.encode(code),
                expiresAt
        ));

        return new PendingTwoFactorChallenge(challengeId, user.getEmail(), code, expiresAt);
    }

    @Transactional(noRollbackFor = {
            ExpiredTwoFactorCodeException.class,
            UnauthorizedException.class
    })
    public PendingTwoFactorChallenge resendChallenge(String challengeId) {
        Instant now = Instant.now();
        TwoFactorChallenge challenge = challengeRepository.findForUpdateByChallengeId(challengeId)
                .orElseThrow(() -> new UnauthorizedException("Подтверждение 2FA недействительно или истекло"));

        if (challenge.isConsumed()) {
            throw new UnauthorizedException("Подтверждение 2FA недействительно или истекло");
        }

        if (challenge.isExpired(now)) {
            challenge.markConsumed(now);
            throw new ExpiredTwoFactorCodeException("Срок действия кода 2FA истёк");
        }

        challenge.markConsumed(now);
        return createChallenge(challenge.getUser());
    }

    @Transactional(noRollbackFor = {
            InvalidTwoFactorCodeException.class,
            ExpiredTwoFactorCodeException.class,
            UnauthorizedException.class
    })
    public User verifyCode(String challengeId, String code) {
        Instant now = Instant.now();
        TwoFactorChallenge challenge = challengeRepository.findForUpdateByChallengeId(challengeId)
                .orElseThrow(() -> new UnauthorizedException("Подтверждение 2FA недействительно или истекло"));

        if (challenge.isConsumed()) {
            throw new UnauthorizedException("Подтверждение 2FA недействительно или истекло");
        }

        if (challenge.isExpired(now)) {
            challenge.markConsumed(now);
            throw new ExpiredTwoFactorCodeException("Срок действия кода 2FA истёк");
        }

        if (!passwordEncoder.matches(code, challenge.getCodeHash())) {
            int attempts = challenge.incrementFailedAttempts();
            if (attempts >= maxAttempts) {
                challenge.markConsumed(now);
            }
            throw new InvalidTwoFactorCodeException("Неверный код 2FA");
        }

        challenge.markConsumed(now);
        return challenge.getUser();
    }

    @Transactional
    public void invalidateChallengesForUser(Long userId) {
        challengeRepository.invalidateActiveChallenges(userId, Instant.now());
    }

    private String generateCode() {
        return "%06d".formatted(secureRandom.nextInt(1_000_000));
    }

    public record PendingTwoFactorChallenge(
            String challengeId,
            String email,
            String code,
            Instant expiresAt
    ) {
    }
}