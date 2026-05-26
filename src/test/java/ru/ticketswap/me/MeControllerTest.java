package ru.ticketswap.me;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import ru.ticketswap.auth.TwoFactorService;
import ru.ticketswap.common.UnauthorizedException;
import ru.ticketswap.hold.ListingHoldRepository;
import ru.ticketswap.me.dto.MeProfileResponse;
import ru.ticketswap.me.dto.TwoFactorStatusResponse;
import ru.ticketswap.me.dto.TwoFactorToggleRequest;
import ru.ticketswap.me.dto.UpdateMeRequest;
import ru.ticketswap.purchase.PurchaseOrderRepository;
import ru.ticketswap.ticket.TicketRepository;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserIdentityService;
import ru.ticketswap.user.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ListingHoldRepository listingHoldRepository;

    @Mock
    private UserIdentityService userIdentityService;

    @Mock
    private TwoFactorService twoFactorService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private UserDetails principal;

    private MeController meController;

    @BeforeEach
    void setUp() {
        meController = new MeController(
                userRepository,
                ticketRepository,
                listingHoldRepository,
                userIdentityService,
                twoFactorService,
                passwordEncoder,
                purchaseOrderRepository
        );
    }

    @Test
    void profileIncludesTwoFactorStatus() {
        User user = authenticatedUser();
        user.setTwoFactorEnabled(true);

        ResponseEntity<MeProfileResponse> response = meController.getProfile(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().twoFactorEnabled());
    }

    @Test
    void enableTwoFactorUpdatesUser() {
        User user = authenticatedUser();
        TwoFactorToggleRequest request = new TwoFactorToggleRequest("password123");

        when(passwordEncoder.matches("password123", user.getPasswordHash())).thenReturn(true);

        ResponseEntity<TwoFactorStatusResponse> response = meController.enableTwoFactor(principal, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().twoFactorEnabled());
        assertTrue(user.isTwoFactorEnabled());

        verify(userRepository).save(user);
        verify(twoFactorService).invalidateChallengesForUser(user.getId());
    }

    @Test
    void disableTwoFactorUpdatesUserAndInvalidatesChallenges() {
        User user = authenticatedUser();
        user.setTwoFactorEnabled(true);
        TwoFactorToggleRequest request = new TwoFactorToggleRequest("password123");

        when(passwordEncoder.matches("password123", user.getPasswordHash())).thenReturn(true);

        ResponseEntity<TwoFactorStatusResponse> response = meController.disableTwoFactor(principal, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().twoFactorEnabled());
        assertFalse(user.isTwoFactorEnabled());

        verify(userRepository).save(user);
        verify(twoFactorService).invalidateChallengesForUser(user.getId());
    }

    @Test
    void updateProfileChangesLoginWithPasswordAndInvalidatesTokens() {
        User user = authenticatedUser();
        user.setLogin("old-login");
        UpdateMeRequest request = new UpdateMeRequest("new-login", "password123");

        when(userIdentityService.normalizeLogin("new-login")).thenReturn("new-login");
        when(passwordEncoder.matches("password123", user.getPasswordHash())).thenReturn(true);

        ResponseEntity<MeProfileResponse> response = meController.updateProfile(principal, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("new-login", response.getBody().login());
        assertEquals(1, user.getTokenVersion());

        verify(userIdentityService).assertLoginAvailable("new-login", user.getId());
        verify(userRepository).save(user);
    }

    @Test
    void updateProfileRejectsLoginChangeWithoutValidPassword() {
        User user = authenticatedUser();
        user.setLogin("old-login");
        UpdateMeRequest request = new UpdateMeRequest("new-login", "wrong-password");

        when(userIdentityService.normalizeLogin("new-login")).thenReturn("new-login");
        when(passwordEncoder.matches("wrong-password", user.getPasswordHash())).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> meController.updateProfile(principal, request));

        assertEquals("old-login", user.getLogin());
        assertEquals(0, user.getTokenVersion());
        verify(userIdentityService, never()).assertLoginAvailable("new-login", user.getId());
        verify(userRepository, never()).save(user);
    }

    @Test
    void updateProfileKeepsSameLoginWithoutPassword() {
        User user = authenticatedUser();
        user.setLogin("same-login");
        UpdateMeRequest request = new UpdateMeRequest("same-login", null);

        when(userIdentityService.normalizeLogin("same-login")).thenReturn("same-login");

        ResponseEntity<MeProfileResponse> response = meController.updateProfile(principal, request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("same-login", response.getBody().login());
        assertEquals(0, user.getTokenVersion());

        verify(passwordEncoder, never()).matches(null, user.getPasswordHash());
        verify(userIdentityService, never()).assertLoginAvailable("same-login", user.getId());
        verify(userRepository).save(user);
    }

    private User authenticatedUser() {
        User user = new User("user@example.com", "hash");
        ReflectionTestUtils.setField(user, "id", 1L);
        user.setEmailVerified(true);

        when(principal.getUsername()).thenReturn("user@example.com");
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        return user;
    }
}
