package ru.ticketswap.user;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import ru.ticketswap.common.ConflictException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserIdentityServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserIdentityService userIdentityService;

    @BeforeEach
    void setUp() {
        userIdentityService = new UserIdentityService(userRepository);
    }

    @Test
    void loadUserDetailsByIdentifierUsesNormalizedEmail() {
        User user = new User("user@example.com", "hash");

        when(userRepository.findAllByEmailIgnoreCase("user@example.com"))
                .thenReturn(List.of(user));

        UserDetails userDetails = userIdentityService.loadUserDetailsByIdentifier("  USER@example.com ");

        assertEquals("user@example.com", userDetails.getUsername());
        verify(userRepository).findAllByEmailIgnoreCase("user@example.com");
    }

    @Test
    void loadUserDetailsByIdentifierUsesNormalizedPhone() {
        User user = new User("user@example.com", "hash");
        user.setPhoneNumber("+79991234567");

        when(userRepository.findByPhoneNumber("+79991234567"))
                .thenReturn(Optional.of(user));

        UserDetails userDetails = userIdentityService.loadUserDetailsByIdentifier("+7 (999) 123-45-67");

        assertEquals("user@example.com", userDetails.getUsername());
        verify(userRepository).findByPhoneNumber("+79991234567");
    }

    @Test
    void normalizeLoginRejectsPhoneLikeValues() {
        assertThrows(IllegalArgumentException.class, () -> userIdentityService.normalizeLogin("79991234567"));
    }

    @Test
    void normalizeLoginRejectsHyphenOnlyPhoneLikeValues() {
        assertThrows(IllegalArgumentException.class, () -> userIdentityService.normalizeLogin("-----"));
    }

    @Test
    void normalizeLoginRejectsEmailLikeValues() {
        assertThrows(IllegalArgumentException.class, () -> userIdentityService.normalizeLogin("user@example.com"));
    }

    @Test
    void normalizeLoginTrimsAndKeepsValidValue() {
        assertEquals("valid.user", userIdentityService.normalizeLogin("  valid.user  "));
    }

    @Test
    void normalizePhoneRejectsInvalidCharacters() {
        assertThrows(IllegalArgumentException.class, () -> userIdentityService.normalizePhone("abc12345"));
    }

    @Test
    void assertPhoneAvailableRejectsCollisionWithExistingLogin() {
        User other = new User("other@example.com", "hash");
        other.setLogin("+79991234567");

        when(userRepository.findByPhoneNumber("+79991234567"))
                .thenReturn(Optional.empty());
        when(userRepository.findByLogin("+79991234567"))
                .thenReturn(Optional.of(other));

        ConflictException ex = assertThrows(
                ConflictException.class,
                () -> userIdentityService.assertPhoneAvailable("+7 (999) 123-45-67", 42L)
        );

        assertEquals("Номер телефона конфликтует с существующим логином", ex.getMessage());
    }

    @Test
    void loadUserDetailsByIdentifierFailsWhenEmailIsAmbiguous() {
        User first = new User("first@example.com", "hash");
        User second = new User("second@example.com", "hash");

        when(userRepository.findAllByEmailIgnoreCase("user@example.com"))
                .thenReturn(List.of(first, second));

        assertThrows(
                UsernameNotFoundException.class,
                () -> userIdentityService.loadUserDetailsByIdentifier("user@example.com")
        );
    }
}