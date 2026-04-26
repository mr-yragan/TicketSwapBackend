package ru.ticketswap.ticket.history;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.ticketswap.ticket.TicketLot;
import ru.ticketswap.ticket.TicketRepository;
import ru.ticketswap.ticket.TicketStatus;
import ru.ticketswap.user.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingStatusHistoryServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ListingStatusHistoryRepository listingStatusHistoryRepository;

    @Test
    void createListingWithInitialStatusStoresHistoryEntry() {
        ListingStatusHistoryService service = new ListingStatusHistoryService(ticketRepository, listingStatusHistoryRepository);
        User seller = new User("seller@example.com", "hash");
        TicketLot ticket = new TicketLot(
                "uid-1",
                "Концерт",
                LocalDateTime.now().plusDays(10),
                "Арена",
                "Берлин",
                BigDecimal.valueOf(150),
                null,
                null,
                null,
                seller
        );

        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);

        TicketLot saved = service.createListingWithInitialStatus(ticket, "Объявление создано", seller);

        assertEquals(ticket, saved);

        ArgumentCaptor<ListingStatusHistory> historyCaptor = ArgumentCaptor.forClass(ListingStatusHistory.class);
        verify(listingStatusHistoryRepository).save(historyCaptor.capture());

        ListingStatusHistory history = historyCaptor.getValue();
        assertEquals(null, history.getFromStatus());
        assertEquals(TicketStatus.CREATED, history.getToStatus());
        assertEquals("Объявление создано", history.getReason());
        assertEquals(seller, history.getChangedByUser());
        assertNotNull(history.getChangedAt());
    }

    @Test
    void transitionChangesStatusAndStoresHistoryEntry() {
        ListingStatusHistoryService service = new ListingStatusHistoryService(ticketRepository, listingStatusHistoryRepository);
        User seller = new User("seller@example.com", "hash");
        TicketLot ticket = new TicketLot(
                "uid-1",
                "Концерт",
                LocalDateTime.now().plusDays(10),
                "Арена",
                "Берлин",
                BigDecimal.valueOf(150),
                null,
                null,
                null,
                seller
        );

        when(ticketRepository.saveAndFlush(ticket)).thenReturn(ticket);

        TicketLot saved = service.transition(ticket, TicketStatus.PENDING_VALIDATION, "Проверка начата", null);

        assertEquals(ticket, saved);
        assertEquals(TicketStatus.PENDING_VALIDATION, ticket.getStatus());
        verify(ticketRepository).saveAndFlush(ticket);
        verify(listingStatusHistoryRepository).save(any(ListingStatusHistory.class));
    }

    @Test
    void transitionDoesNothingWhenStatusIsUnchanged() {
        ListingStatusHistoryService service = new ListingStatusHistoryService(ticketRepository, listingStatusHistoryRepository);
        User seller = new User("seller@example.com", "hash");
        TicketLot ticket = new TicketLot(
                "uid-1",
                "Концерт",
                LocalDateTime.now().plusDays(10),
                "Арена",
                "Берлин",
                BigDecimal.valueOf(150),
                null,
                null,
                null,
                seller
        );

        TicketLot saved = service.transition(ticket, TicketStatus.CREATED, "Без изменений", seller);

        assertEquals(ticket, saved);
        verify(ticketRepository, times(0)).saveAndFlush(any());
        verify(listingStatusHistoryRepository, times(0)).save(any());
    }
}