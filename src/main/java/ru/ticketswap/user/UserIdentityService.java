package ru.ticketswap.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.ticketswap.common.ConflictException;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
            throw new IllegalArgumentException("Логин не должен быть пустым");
        }
        if (normalized.contains("@")) {
            throw new IllegalArgumentException("Логин не должен выглядеть как почта");
        }
        if (!LOGIN_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    "Логин может содержать только буквы, цифры, нижнее подчёркивание, точку и дефис, длина от 3 до 32 символов"
            );
        }
        if (PHONE_LIKE_LOGIN_PATTERN.matcher(normalized).matches() || isPhoneLike(normalized)) {
            throw new IllegalArgumentException("Логин не должен выглядеть как номер телефона");
        }
        return normalized;
    }

    public String normalizePhone(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }

        String trimmed = phoneNumber.trim();
        if (!trimmed.matches(PHONE_CHARS_REGEX)) {
            throw new IllegalArgumentException("Номер телефона содержит недопустимые символы");
        }

        String digits = trimmed.replaceAll(DIGITS_ONLY_REGEX, "");
        if (digits.isEmpty()) {
            throw new IllegalArgumentException("Номер телефона должен содержать цифры");
        }
        if (digits.length() < 5 || digits.length() > 32) {
            throw new IllegalArgumentException("Номер телефона должен содержать от 5 до 32 цифр");
        }
        return "+" + digits;
    }

    public UserDetails loadUserDetailsByIdentifier(String identifier) {
        User user;
        try {
            user = findUserByIdentifier(identifier)
                    .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
        } catch (DuplicateUserIdentityException ex) {
            throw new UsernameNotFoundException("Пользователь не найден", ex);
        }

        return toUserDetails(user);
    }

    public UserDetails loadUserDetailsByEmail(String email) {
        User user;
        try {
            user = findUserByEmail(email)
                    .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден"));
        } catch (DuplicateUserIdentityException ex) {
            throw new UsernameNotFoundException("Пользователь не найден", ex);
        }

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
                .filter(other -> isDifferentUser(other, currentUserId))
                .ifPresent(other -> {
                    throw new ConflictException("Логин уже используется");
                });

        userRepository.findByPhoneNumber(normalizedLogin)
                .filter(other -> isDifferentUser(other, currentUserId))
                .ifPresent(other -> {
                    throw new ConflictException("Логин конфликтует с существующим номером телефона");
                });
    }

    public void assertPhoneAvailable(String phoneNumber, Long currentUserId) {
        String normalizedPhone = normalizePhone(phoneNumber);

        userRepository.findByPhoneNumber(normalizedPhone)
                .filter(other -> isDifferentUser(other, currentUserId))
                .ifPresent(other -> {
                    throw new ConflictException("Номер телефона уже используется");
                });

        userRepository.findByLogin(normalizedPhone)
                .filter(other -> isDifferentUser(other, currentUserId))
                .ifPresent(other -> {
                    throw new ConflictException("Номер телефона конфликтует с существующим логином");
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
            log.error("Найдено несколько пользователей для {}", source);
            throw new DuplicateUserIdentityException(source);
        }
        return Optional.of(users.get(0));
    }

    private boolean isDifferentUser(User other, Long currentUserId) {
        return !Objects.equals(other.getId(), currentUserId);
    }
}