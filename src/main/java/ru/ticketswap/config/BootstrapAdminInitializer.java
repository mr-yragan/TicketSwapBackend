package ru.ticketswap.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserIdentityService;
import ru.ticketswap.user.UserRepository;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final String ADMIN_ROLE = "ADMIN";

    private final TicketSwapProperties properties;
    private final UserRepository userRepository;
    private final UserIdentityService userIdentityService;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(
            TicketSwapProperties properties,
            UserRepository userRepository,
            UserIdentityService userIdentityService,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.userIdentityService = userIdentityService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        TicketSwapProperties.Bootstrap.Admin admin = properties.getBootstrap().getAdmin();
        if (!admin.isEnabled()) {
            return;
        }

        String email = requireValue(admin.getEmail(), "TICKETSWAP_BOOTSTRAP_ADMIN_EMAIL");
        String login = requireValue(admin.getLogin(), "TICKETSWAP_BOOTSTRAP_ADMIN_LOGIN");
        String password = requireValue(admin.getPassword(), "TICKETSWAP_BOOTSTRAP_ADMIN_PASSWORD");

        String normalizedEmail = userIdentityService.normalizeEmail(email);
        String normalizedLogin = userIdentityService.normalizeLogin(login);

        if (password.length() < 12) {
            throw new IllegalStateException("Bootstrap admin password must be at least 12 characters");
        }

        userIdentityService.findUserByEmail(normalizedEmail).ifPresentOrElse(existing -> {
            if (!ADMIN_ROLE.equalsIgnoreCase(existing.getRole())) {
                existing.setRole(ADMIN_ROLE);
                existing.incrementTokenVersion();
            }
            existing.setEmailVerified(true);
            if (existing.getLogin() == null || existing.getLogin().isBlank()) {
                existing.setLogin(normalizedLogin);
            }
            userRepository.save(existing);
        }, () -> {
            userIdentityService.assertLoginAvailable(normalizedLogin, null);
            User user = new User(normalizedEmail, passwordEncoder.encode(password));
            user.setLogin(normalizedLogin);
            user.setRole(ADMIN_ROLE);
            user.setEmailVerified(true);
            userRepository.save(user);
        });
    }

    private String requireValue(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(envName + " is required when bootstrap admin is enabled");
        }
        return value.trim();
    }
}
