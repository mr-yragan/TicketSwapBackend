package ru.ticketswap.ticket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ticketswap.ticket.history.ListingStatusHistoryService;
import ru.ticketswap.user.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingWriteServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ListingStatusHistoryService listingStatusHistoryService;

    @Test
    void prepareForRevalidationClearsBuyerAndRecordsHistory() {
        ListingWriteService service = new ListingWriteService(ticketRepository, listingStatusHistoryService);
        User seller = new User("seller@example.com", "hash");
        User buyer = new User("buyer@example.com", "hash");
        TicketLot listing = new TicketLot(
                "uid-1",
                "Концерт",
                LocalDateTime.now().plusDays(10),
                "Арена",
                "Берлин",
                BigDecimal.valueOf(150),
                null,
                "org1",
                null,
                seller
        );
        listing.setStatus(TicketStatus.PENDING_RECIPIENT);
        listing.setBuyer(buyer);

        when(ticketRepository.saveAndFlush(listing)).thenReturn(listing);

        TicketLot saved = service.prepareForRevalidation(listing, seller);

        assertEquals(listing, saved);
        assertEquals(TicketStatus.CREATED, listing.getStatus());
        assertNull(listing.getBuyer());

        verify(ticketRepository).saveAndFlush(listing);
        verify(listingStatusHistoryService).recordStatus(
                listing,
                TicketStatus.PENDING_RECIPIENT,
                TicketStatus.CREATED,
                ListingWriteService.REVALIDATION_REASON,
                seller
        );
    }

    @Test
    void prepareForRevalidationRecordsReasonEvenWhenStatusIsAlreadyCreated() {
        ListingWriteService service = new ListingWriteService(ticketRepository, listingStatusHistoryService);
        User seller = new User("seller@example.com", "hash");
        TicketLot listing = new TicketLot(
                "uid-1",
                "Концерт",
                LocalDateTime.now().plusDays(10),
                "Арена",
                "Берлин",
                BigDecimal.valueOf(150),
                null,
                "org1",
                null,
                seller
        );
        when(ticketRepository.saveAndFlush(listing)).thenReturn(listing);

        service.prepareForRevalidation(listing, seller);

        assertEquals(TicketStatus.CREATED, listing.getStatus());

        verify(ticketRepository).saveAndFlush(listing);
        verify(listingStatusHistoryService).recordStatus(
                listing,
                TicketStatus.CREATED,
                TicketStatus.CREATED,
                ListingWriteService.REVALIDATION_REASON,
                seller
        );
    }
}