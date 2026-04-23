package ru.ticketswap.me;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import ru.ticketswap.auth.TwoFactorService;
import ru.ticketswap.hold.ListingHoldRepository;
import ru.ticketswap.me.dto.MeProfileResponse;
import ru.ticketswap.me.dto.TwoFactorStatusResponse;
import ru.ticketswap.ticket.TicketRepository;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserIdentityService;
import ru.ticketswap.user.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private UserDetails principal;

    @InjectMocks
    private MeController meController;

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

        ResponseEntity<TwoFactorStatusResponse> response = meController.enableTwoFactor(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().twoFactorEnabled());
        assertTrue(user.isTwoFactorEnabled());
        verify(userRepository).save(user);
        verify(twoFactorService, never()).invalidateChallengesForUser(user.getId());
    }

    @Test
    void disableTwoFactorUpdatesUserAndInvalidatesChallenges() {
        User user = authenticatedUser();
        user.setTwoFactorEnabled(true);

        ResponseEntity<TwoFactorStatusResponse> response = meController.disableTwoFactor(principal);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().twoFactorEnabled());
        verify(userRepository).save(user);
        verify(twoFactorService).invalidateChallengesForUser(user.getId());
    }

    private User authenticatedUser() {
        User user = new User("user@example.com", "hash");
        user.setEmailVerified(true);

        when(principal.getUsername()).thenReturn("user@example.com");
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        return user;
    }
}
