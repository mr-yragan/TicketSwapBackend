package ru.ticketswap.ticket;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ru.ticketswap.hold.ListingHoldRepository;
import ru.ticketswap.partner.PartnerOrganizerCodeMapper;
import ru.ticketswap.purchase.PurchaseService;
import ru.ticketswap.storage.TicketFileStorageService;
import ru.ticketswap.ticket.dto.CreateTicketRequest;
import ru.ticketswap.ticket.history.ListingStatusHistoryService;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class TicketControllerRevalidationTest {

    @Test
    void requiresRevalidationDoesNotTriggerWhenOrganizerNameDiffersOnlyByCaseAndWhitespace() {
        TicketController controller = createController();
        User seller = new User("seller@example.com", "hash");
        TicketLot listing = new TicketLot(
                "uid-1",
                "Concert",
                LocalDateTime.now().plusDays(10),
                "Arena",
                "Berlin",
                BigDecimal.valueOf(150),
                null,
                " org1 ",
                null,
                seller
        );
        listing.setStatus(TicketStatus.PENDING_RECIPIENT);

        CreateTicketRequest request = new CreateTicketRequest(
                "uid-1",
                "Concert",
                listing.getEventDate(),
                "Arena, Berlin",
                BigDecimal.valueOf(150),
                null,
                "ORG1",
                null
        );

        Object venueParts = ReflectionTestUtils.invokeMethod(controller, "parseVenueParts", request.venue());
        boolean requiresRevalidation = ReflectionTestUtils.invokeMethod(
                controller,
                "requiresRevalidation",
                listing,
                request,
                venueParts
        );

        assertFalse(requiresRevalidation);
    }

    @Test
    void requiresRevalidationTriggersWhenOrganizerNameNormalizedValueChanges() {
        TicketController controller = createController();
        User seller = new User("seller@example.com", "hash");
        TicketLot listing = new TicketLot(
                "uid-1",
                "Concert",
                LocalDateTime.now().plusDays(10),
                "Arena",
                "Berlin",
                BigDecimal.valueOf(150),
                null,
                "org1",
                null,
                seller
        );
        listing.setStatus(TicketStatus.PENDING_RECIPIENT);

        CreateTicketRequest request = new CreateTicketRequest(
                "uid-1",
                "Concert",
                listing.getEventDate(),
                "Arena, Berlin",
                BigDecimal.valueOf(150),
                null,
                "org2",
                null
        );

        Object venueParts = ReflectionTestUtils.invokeMethod(controller, "parseVenueParts", request.venue());
        boolean requiresRevalidation = ReflectionTestUtils.invokeMethod(
                controller,
                "requiresRevalidation",
                listing,
                request,
                venueParts
        );

        assertTrue(requiresRevalidation);
    }

    @Test
    void requiresRevalidationTriggersWhenOrganizerMappingChangesFromSupportedToUnsupported() {
        TicketController controller = createController();
        User seller = new User("seller@example.com", "hash");
        TicketLot listing = new TicketLot(
                "uid-1",
                "Concert",
                LocalDateTime.now().plusDays(10),
                "Arena",
                "Berlin",
                BigDecimal.valueOf(150),
                null,
                "org1",
                null,
                seller
        );
        listing.setStatus(TicketStatus.PENDING_RECIPIENT);

        CreateTicketRequest request = new CreateTicketRequest(
                "uid-1",
                "Concert",
                listing.getEventDate(),
                "Arena, Berlin",
                BigDecimal.valueOf(150),
                null,
                "Amazing Organizer",
                null
        );

        Object venueParts = ReflectionTestUtils.invokeMethod(controller, "parseVenueParts", request.venue());
        boolean requiresRevalidation = ReflectionTestUtils.invokeMethod(
                controller,
                "requiresRevalidation",
                listing,
                request,
                venueParts
        );

        assertTrue(requiresRevalidation);
    }

    private TicketController createController() {
        return new TicketController(
                mock(TicketRepository.class),
                mock(UserRepository.class),
                mock(ListingHoldRepository.class),
                mock(PurchaseService.class),
                mock(ListingLifecycleService.class),
                mock(ListingWriteService.class),
                new PartnerOrganizerCodeMapper(),
                mock(TicketFileStorageService.class),
                mock(ListingStatusHistoryService.class)
        );
    }
}
