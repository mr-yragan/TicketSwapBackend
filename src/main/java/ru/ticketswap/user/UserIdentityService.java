package ru.ticketswap.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.ticketswap.common.ConflictException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UserIdentityService {

    private static final Logger log = LoggerFactory.getLogger(UserIdentityService.class);

    private static final Pattern LOGIN_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{3,32}$");
    private static final Pattern PHONE_LIKE_LOGIN_PATTERN = Pattern.compile("^[0-9-]{5,32}$");
    private static final String PHONE_CHARS_REGEX = "^[+0-9 ()-]+$";
    private static final String DIGITS_ONLY_REGEX = "\\D";

    private final UserRepository userRepository;

    public UserIdentityService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizeLogin(String login) {
        if (login == null) {
            return null;
        }

        String normalized = login.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Login must not be blank");
        }
        if (normalized.contains("@")) {
            throw new IllegalArgumentException("Login must not look like an email");
        }
        if (!LOGIN_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Login may contain only letters, digits, underscore, dot and dash, and must be between 3 and 32 characters"
            );
        }
        if (PHONE_LIKE_LOGIN_PATTERN.matcher(normalized).matches() || isPhoneLike(normalized)) {
            throw new IllegalArgumentException("Login must not look like a phone number");
        }
        return normalized;
    }

    public String normalizePhone(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }

        String trimmed = phoneNumber.trim();
        if (!trimmed.matches(PHONE_CHARS_REGEX)) {
            throw new IllegalArgumentException("Phone number has invalid characters");
        }

        String digits = trimmed.replaceAll(DIGITS_ONLY_REGEX, "");
        if (digits.isEmpty()) {
            throw new IllegalArgumentException("Phone number must contain digits");
        }
        if (digits.length() < 5 || digits.length() > 32) {
            throw new IllegalArgumentException("Phone number must contain between 5 and 32 digits");
        }
        return "+" + digits;
    }

    public UserDetails loadUserDetailsByIdentifier(String identifier) {
        User user = findUserByIdentifier(identifier)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return toUserDetails(user);
    }

    public UserDetails loadUserDetailsByEmail(String email) {
        User user = findUserByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return toUserDetails(user);
    }

    public Optional<User> findUserByEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail == null || normalizedEmail.isBlank()) {
            return Optional.empty();
        }
        return ensureUnique(userRepository.findAllByEmailIgnoreCase(normalizedEmail), "email");
    }

    private UserDetails toUserDetails(User user) {
        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getRole())
                .build();
    }

    public boolean emailExists(String email) {
        return userRepository.existsByEmailIgnoreCase(normalizeEmail(email));
    }

    public void assertLoginAvailable(String login, Long currentUserId) {
        String normalizedLogin = normalizeLogin(login);

        userRepository.findByLogin(normalizedLogin)
                .filter(other -> !other.getId().equals(currentUserId))
                .ifPresent(other -> {
                    throw new ConflictException("Login already in use");
                });

        userRepository.findByPhoneNumber(normalizedLogin)
                .filter(other -> !other.getId().equals(currentUserId))
                .ifPresent(other -> {
                    throw new ConflictException("Login conflicts with an existing phone number");
                });
    }

    public void assertPhoneAvailable(String phoneNumber, Long currentUserId) {
        String normalizedPhone = normalizePhone(phoneNumber);

        userRepository.findByPhoneNumber(normalizedPhone)
                .filter(other -> !other.getId().equals(currentUserId))
                .ifPresent(other -> {
                    throw new ConflictException("Phone number already in use");
                });

        userRepository.findByLogin(normalizedPhone)
                .filter(other -> !other.getId().equals(currentUserId))
                .ifPresent(other -> {
                    throw new ConflictException("Phone number conflicts with an existing login");
                });
    }

    public boolean isEmailIdentifier(String identifier) {
        return identifier != null && identifier.contains("@");
    }

    public boolean isPhoneLike(String identifier) {
        if (identifier == null) {
            return false;
        }

        String trimmed = identifier.trim();
        if (!trimmed.matches(PHONE_CHARS_REGEX)) {
            return false;
        }

        int digitsCount = trimmed.replaceAll(DIGITS_ONLY_REGEX, "").length();
        return digitsCount >= 5 && digitsCount <= 32;
    }

    private Optional<User> findUserByIdentifier(String identifier) {
        if (identifier == null) {
            return Optional.empty();
        }

        String trimmed = identifier.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }

        if (isEmailIdentifier(trimmed)) {
            return findUserByEmail(trimmed);
        }

        if (isPhoneLike(trimmed)) {
            return userRepository.findByPhoneNumber(normalizePhone(trimmed));
        }

        return userRepository.findByLogin(trimmed);
    }

    private Optional<User> ensureUnique(List<User> users, String source) {
        if (users.isEmpty()) {
            return Optional.empty();
        }
        if (users.size() > 1) {
            log.error("Multiple users found for {}", source);
            throw new DuplicateUserIdentityException(source);
        }
        return Optional.of(users.get(0));
    }
}
