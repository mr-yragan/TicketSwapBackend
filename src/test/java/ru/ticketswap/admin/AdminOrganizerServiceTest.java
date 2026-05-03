package ru.ticketswap.admin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.ticketswap.admin.dto.CreateOrganizerRequest;
import ru.ticketswap.audit.AuditLogService;
import ru.ticketswap.common.ConflictException;
import ru.ticketswap.common.NotFoundException;
import ru.ticketswap.hold.ListingHoldRepository;
import ru.ticketswap.notification.NotificationOutboxService;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerRepository;
import ru.ticketswap.purchase.PurchaseOrderRepository;
import ru.ticketswap.ticket.TicketRepository;
import ru.ticketswap.ticket.history.ListingStatusHistoryService;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserIdentityService;
import ru.ticketswap.user.UserRepository;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ListingHoldRepository listingHoldRepository;

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private ListingStatusHistoryService listingStatusHistoryService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private NotificationOutboxService notificationOutboxService;

    @Test
    void createOrganizerCreatesOrganizerAndPromotesExistingUser() {
        AdminOrganizerService service = createService();

        CreateOrganizerRequest request = new CreateOrganizerRequest(
                "Большая концертная организация ",
                "Organizer@Example.com",
                "org3"
        );

        User user = new User("organizer@example.com", "hash");
        User actor = new User("admin@example.com", "hash");

        Organizer saved = new Organizer(
                "Большая концертная организация",
                "org3",
                "organizer@example.com"
        );

        when(userIdentityService.normalizeEmail("Organizer@Example.com"))
                .thenReturn("organizer@example.com");
        when(userIdentityService.findUserByEmail("organizer@example.com"))
                .thenReturn(Optional.of(user));
        when(passwordEncoder.encode(anyString()))
                .thenReturn("hashed-secret");
        when(organizerRepository.save(any(Organizer.class)))
                .thenReturn(saved);

        AdminOrganizerService.OrganizerCreationResult result = service.createOrganizer(request, actor);

        assertEquals(saved, result.organizer());
        assertNotNull(result.generatedIntegrationSecret());
        assertEquals("ORGANIZER", user.getRole());

        verify(userRepository).save(user);

        verify(organizerRepository).save(argThat(organizer ->
                "Большая концертная организация".equals(organizer.getName())
                        && "org3".equals(organizer.getOrganizerCode())
                        && "organizer@example.com".equals(organizer.getContactEmail())
        ));

        verify(auditLogService).record(
                actor,
                "ORGANIZER_CREATED",
                "ORGANIZER",
                saved.getId(),
                "code=" + saved.getOrganizerCode()
        );

        verify(notificationOutboxService).enqueue(
                "organizer@example.com",
                "ORGANIZER_CREATED",
                "Профиль организатора создан",
                "Организатор: Большая концертная организация"
        );
    }

    @Test
    void createOrganizerReturns404WhenUserDoesNotExist() {
        AdminOrganizerService service = createService();

        CreateOrganizerRequest request = new CreateOrganizerRequest(
                "Организация",
                "missing@example.com",
                "org3"
        );

        User actor = new User("admin@example.com", "hash");

        when(userIdentityService.normalizeEmail("missing@example.com"))
                .thenReturn("missing@example.com");
        when(userIdentityService.findUserByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.createOrganizer(request, actor));
    }

    @Test
    void createOrganizerRejectsDuplicateOrganizerCode() {
        AdminOrganizerService service = createService();

        CreateOrganizerRequest request = new CreateOrganizerRequest(
                "Организация",
                "organizer@example.com",
                "org3"
        );

        User user = new User("organizer@example.com", "hash");
        User actor = new User("admin@example.com", "hash");

        when(userIdentityService.normalizeEmail("organizer@example.com"))
                .thenReturn("organizer@example.com");
        when(userIdentityService.findUserByEmail("organizer@example.com"))
                .thenReturn(Optional.of(user));
        when(organizerRepository.existsByOrganizerCodeIgnoreCase("org3"))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> service.createOrganizer(request, actor));
    }

    @Test
    void createOrganizerRejectsDuplicateContactEmail() {
        AdminOrganizerService service = createService();

        CreateOrganizerRequest request = new CreateOrganizerRequest(
                "Организация",
                "organizer@example.com",
                "org3"
        );

        User user = new User("organizer@example.com", "hash");
        User actor = new User("admin@example.com", "hash");

        when(userIdentityService.normalizeEmail("organizer@example.com"))
                .thenReturn("organizer@example.com");
        when(userIdentityService.findUserByEmail("organizer@example.com"))
                .thenReturn(Optional.of(user));
        when(organizerRepository.existsByContactEmailIgnoreCase("organizer@example.com"))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> service.createOrganizer(request, actor));
    }

    @Test
    void createOrganizerRejectsDuplicateName() {
        AdminOrganizerService service = createService();

        CreateOrganizerRequest request = new CreateOrganizerRequest(
                "Организация",
                "organizer@example.com",
                "org3"
        );

        User user = new User("organizer@example.com", "hash");
        User actor = new User("admin@example.com", "hash");

        when(userIdentityService.normalizeEmail("organizer@example.com"))
                .thenReturn("organizer@example.com");
        when(userIdentityService.findUserByEmail("organizer@example.com"))
                .thenReturn(Optional.of(user));
        when(organizerRepository.existsByNameIgnoreCase("Организация"))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> service.createOrganizer(request, actor));
    }

    private AdminOrganizerService createService() {
        return new AdminOrganizerService(
                organizerRepository,
                userRepository,
                userIdentityService,
                ticketRepository,
                listingHoldRepository,
                purchaseOrderRepository,
                listingStatusHistoryService,
                passwordEncoder,
                auditLogService,
                notificationOutboxService
        );
    }
}