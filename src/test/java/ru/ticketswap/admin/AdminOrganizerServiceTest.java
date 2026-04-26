package ru.ticketswap.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ticketswap.admin.dto.CreateOrganizerRequest;
import ru.ticketswap.common.ConflictException;
import ru.ticketswap.common.NotFoundException;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerRepository;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserIdentityService;
import ru.ticketswap.user.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminOrganizerServiceTest {

    @Mock
    private OrganizerRepository organizerRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserIdentityService userIdentityService;

    @Test
    void createOrganizerCreatesOrganizerAndPromotesExistingUser() {
        AdminOrganizerService service = createService();
        CreateOrganizerRequest request = new CreateOrganizerRequest(
                "Большая концертная организация ",
                "Organizer@Example.com",
                "org3"
        );
        User user = new User("organizer@example.com", "hash");
        Organizer saved = new Organizer("Большая концертная организация", "org3", "organizer@example.com");

        when(userIdentityService.normalizeEmail("Organizer@Example.com")).thenReturn("organizer@example.com");
        when(userIdentityService.findUserByEmail("organizer@example.com")).thenReturn(Optional.of(user));
        when(organizerRepository.existsByApiKeyIgnoreCase("org3")).thenReturn(false);
        when(organizerRepository.existsByContactEmailIgnoreCase("organizer@example.com")).thenReturn(false);
        when(organizerRepository.save(org.mockito.ArgumentMatchers.any(Organizer.class))).thenReturn(saved);

        Organizer result = service.createOrganizer(request);

        assertEquals(saved, result);
        assertEquals("ORGANIZER", user.getRole());
        verify(userRepository).save(user);
        verify(organizerRepository).save(org.mockito.ArgumentMatchers.argThat(organizer ->
                "Большая концертная организация".equals(organizer.getName())
                        && "org3".equals(organizer.getApiKey())
                        && "organizer@example.com".equals(organizer.getContactEmail())
        ));
    }

    @Test
    void createOrganizerReturns404WhenUserDoesNotExist() {
        AdminOrganizerService service = createService();
        CreateOrganizerRequest request = new CreateOrganizerRequest("Организация", "missing@example.com", "org3");

        when(userIdentityService.normalizeEmail("missing@example.com")).thenReturn("missing@example.com");
        when(userIdentityService.findUserByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.createOrganizer(request));
    }

    @Test
    void createOrganizerRejectsDuplicateApiKey() {
        AdminOrganizerService service = createService();
        CreateOrganizerRequest request = new CreateOrganizerRequest("Организация", "organizer@example.com", "org3");
        User user = new User("organizer@example.com", "hash");

        when(userIdentityService.normalizeEmail("organizer@example.com")).thenReturn("organizer@example.com");
        when(userIdentityService.findUserByEmail("organizer@example.com")).thenReturn(Optional.of(user));
        when(organizerRepository.existsByApiKeyIgnoreCase("org3")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.createOrganizer(request));
    }

    @Test
    void createOrganizerRejectsDuplicateContactEmail() {
        AdminOrganizerService service = createService();
        CreateOrganizerRequest request = new CreateOrganizerRequest("Организация", "organizer@example.com", "org3");
        User user = new User("organizer@example.com", "hash");

        when(userIdentityService.normalizeEmail("organizer@example.com")).thenReturn("organizer@example.com");
        when(userIdentityService.findUserByEmail("organizer@example.com")).thenReturn(Optional.of(user));
        when(organizerRepository.existsByApiKeyIgnoreCase("org3")).thenReturn(false);
        when(organizerRepository.existsByContactEmailIgnoreCase("organizer@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.createOrganizer(request));
    }

    private AdminOrganizerService createService() {
        return new AdminOrganizerService(organizerRepository, userRepository, userIdentityService);
    }
}