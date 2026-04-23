package ru.ticketswap.auth;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketswap.auth.dto.AuthRequest;
import ru.ticketswap.auth.dto.AuthResponse;
import ru.ticketswap.auth.dto.LoginRequest;
import ru.ticketswap.auth.dto.LoginResponse;
import ru.ticketswap.auth.dto.TwoFactorResendRequest;
import ru.ticketswap.auth.dto.TwoFactorVerifyRequest;
import ru.ticketswap.common.ConflictException;
import ru.ticketswap.common.UnauthorizedException;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserIdentityService;
import ru.ticketswap.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserIdentityService userIdentityService;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final TwoFactorService twoFactorService;
    private final MailService mailService;
    private final EmailVerificationService emailVerificationService;

    public AuthService(
            UserRepository userRepository,
            UserIdentityService userIdentityService,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            TwoFactorService twoFactorService,
            MailService mailService,
            EmailVerificationService emailVerificationService
    ) {
        this.userRepository = userRepository;
        this.userIdentityService = userIdentityService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.twoFactorService = twoFactorService;
        this.mailService = mailService;
        this.emailVerificationService = emailVerificationService;
    }

    @Transactional
    public void register(AuthRequest request) {
        String normalizedEmail = userIdentityService.normalizeEmail(request.email());
        if (userIdentityService.emailExists(normalizedEmail)) {
            throw new ConflictException("Email already exists");
        }

        String normalizedLogin = userIdentityService.normalizeLogin(request.login());
        userIdentityService.assertLoginAvailable(normalizedLogin, null);

        User user = new User(normalizedEmail, passwordEncoder.encode(request.password()));
        user.setLogin(normalizedLogin);
        user.setEmailVerified(false);
        userRepository.save(user);

        emailVerificationService.createAndSendVerificationToken(user);
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.identifier(), request.password())
            );
        } catch (AuthenticationException ex) {
            throw new UnauthorizedException("Invalid credentials");
        }

        User user = userIdentityService.findUserByEmail(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!user.isEmailVerified()) {
            throw new UnauthorizedException("Email is not verified");
        }

        if (!user.isTwoFactorEnabled()) {
            return LoginResponse.authenticated(jwtService.generate(user.getEmail()));
        }

        TwoFactorService.PendingTwoFactorChallenge challenge = twoFactorService.createChallenge(user);
        mailService.sendTwoFactorCode(user.getEmail(), challenge.code(), challenge.expiresAt());
        return LoginResponse.twoFactorRequired(challenge.challengeId(), challenge.expiresAt());
    }

    public AuthResponse verifyTwoFactor(TwoFactorVerifyRequest request) {
        User user = twoFactorService.verifyCode(request.challengeId(), request.code());
        return new AuthResponse(jwtService.generate(user.getEmail()));
    }

    public LoginResponse resendTwoFactor(TwoFactorResendRequest request) {
        TwoFactorService.PendingTwoFactorChallenge challenge = twoFactorService.resendChallenge(request.challengeId());
        mailService.sendTwoFactorCode(challenge.email(), challenge.code(), challenge.expiresAt());
        return LoginResponse.twoFactorRequired(challenge.challengeId(), challenge.expiresAt());
    }
}
