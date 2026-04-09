package ru.ticketswap.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketswap.common.MailDeliveryException;
import ru.ticketswap.config.TicketSwapProperties;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserIdentityService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_SIZE_BYTES = 32;

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserIdentityService userIdentityService;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final TwoFactorService twoFactorService;
    private final long tokenExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            PasswordResetTokenRepository passwordResetTokenRepository,
            UserIdentityService userIdentityService,
            PasswordEncoder passwordEncoder,
            MailService mailService,
            TwoFactorService twoFactorService,
            TicketSwapProperties properties
    ) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.userIdentityService = userIdentityService;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.twoFactorService = twoFactorService;
        this.tokenExpirationMs = properties.getSecurity().getPasswordReset().getTokenExpirationMs();
    }

    @Transactional
    public void requestPasswordReset(String email) {
        String normalizedEmail = userIdentityService.normalizeEmail(email);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return;
        }

        userIdentityService.findUserByEmail(normalizedEmail)
                .ifPresent(this::createAndSendResetToken);
    }

    @Transactional(noRollbackFor = {
            InvalidPasswordResetTokenException.class,
            ExpiredPasswordResetTokenException.class
    })
    public void resetPassword(String token, String newPassword) {
        Instant now = Instant.now();
        String normalizedToken = token == null ? "" : token.trim();
        if (normalizedToken.isBlank()) {
            throw new InvalidPasswordResetTokenException("Password reset token is invalid or already used");
        }

        PasswordResetToken resetToken = passwordResetTokenRepository.findForUpdateByTokenHash(hashToken(normalizedToken))
                .orElseThrow(() -> new InvalidPasswordResetTokenException(
                        "Password reset token is invalid or already used"
                ));

        if (resetToken.isConsumed()) {
            throw new InvalidPasswordResetTokenException("Password reset token is invalid or already used");
        }

        if (resetToken.isExpired(now)) {
            resetToken.markConsumed(now);
            throw new ExpiredPasswordResetTokenException("Password reset token expired");
        }

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        resetToken.markConsumed(now);
        passwordResetTokenRepository.invalidateActiveTokens(user.getId(), now);
        twoFactorService.invalidateChallengesForUser(user.getId());
    }

    private void createAndSendResetToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(tokenExpirationMs);
        String token = generateToken();
        String tokenHash = hashToken(token);

        PasswordResetToken passwordResetToken = passwordResetTokenRepository.save(
                new PasswordResetToken(user, tokenHash, expiresAt)
        );

        try {
            mailService.sendPasswordResetLink(user.getEmail(), token, expiresAt);
        } catch (MailDeliveryException ex) {
            log.warn("Unable to send password reset email to {}", user.getEmail(), ex);
            passwordResetToken.markConsumed(now);
            return;
        }

        passwordResetTokenRepository.invalidateActiveTokensExcept(user.getId(), tokenHash, now);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_SIZE_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
