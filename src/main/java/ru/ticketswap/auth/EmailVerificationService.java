package ru.ticketswap.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketswap.auth.dto.EmailVerificationResponse;
import ru.ticketswap.common.UnauthorizedException;
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
public class EmailVerificationService {

    private static final int TOKEN_SIZE_BYTES = 32;

    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final UserIdentityService userIdentityService;
    private final MailService mailService;
    private final long tokenExpirationMs;
    private final SecureRandom secureRandom = new SecureRandom();

    public EmailVerificationService(
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            UserIdentityService userIdentityService,
            MailService mailService,
            TicketSwapProperties properties
    ) {
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.userIdentityService = userIdentityService;
        this.mailService = mailService;
        this.tokenExpirationMs = properties.getSecurity().getEmailVerification().getTokenExpirationMs();
    }

    @Transactional
    public void createAndSendVerificationToken(User user) {
        if (user.isEmailVerified()) {
            return;
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(tokenExpirationMs);
        String token = generateToken();
        String tokenHash = hashToken(token);

        EmailVerificationToken savedToken = emailVerificationTokenRepository.save(
                new EmailVerificationToken(user, tokenHash, expiresAt)
        );

        mailService.sendEmailVerificationLink(user.getEmail(), token, expiresAt);
        emailVerificationTokenRepository.invalidateActiveTokensExcept(user.getId(), savedToken.getTokenHash(), now);
    }

    @Transactional(noRollbackFor = {IllegalArgumentException.class, UnauthorizedException.class})
    public EmailVerificationResponse verifyEmail(String token) {
        Instant now = Instant.now();
        String normalizedToken = token == null ? "" : token.trim();
        if (normalizedToken.isBlank()) {
            throw new IllegalArgumentException("Токен подтверждения почты недействителен или уже использован");
        }

        EmailVerificationToken verificationToken = emailVerificationTokenRepository
                .findForUpdateByTokenHash(hashToken(normalizedToken))
                .orElseThrow(() -> new IllegalArgumentException("Токен подтверждения почты недействителен или уже использован"));

        if (verificationToken.isConsumed()) {
            throw new IllegalArgumentException("Токен подтверждения почты недействителен или уже использован");
        }

        if (verificationToken.isExpired(now)) {
            verificationToken.markConsumed(now);
            throw new IllegalArgumentException("Срок действия токена подтверждения почты истёк");
        }

        User user = verificationToken.getUser();
        user.setEmailVerified(true);
        verificationToken.markConsumed(now);
        emailVerificationTokenRepository.invalidateActiveTokens(user.getId(), now);

        return new EmailVerificationResponse("Почта успешно подтверждена");
    }

    @Transactional
    public EmailVerificationResponse resendVerification(String email) {
        String normalizedEmail = userIdentityService.normalizeEmail(email);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return new EmailVerificationResponse("Если неподтверждённый аккаунт с такой почтой существует, письмо для подтверждения было отправлено");
        }

        userIdentityService.findUserByEmail(normalizedEmail).ifPresent(user -> {
            if (!user.isEmailVerified()) {
                createAndSendVerificationToken(user);
            }
        });

        return new EmailVerificationResponse("Если неподтверждённый аккаунт с такой почтой существует, письмо для подтверждения было отправлено");
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
            throw new IllegalStateException("SHA-256 недоступен", ex);
        }
    }
}