package ru.ticketswap.purchase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import ru.ticketswap.hold.ListingHold;
import ru.ticketswap.hold.ListingHoldRepository;
import ru.ticketswap.partner.PartnerApiClient;
import ru.ticketswap.partner.PartnerIntegrationException;
import ru.ticketswap.partner.PartnerOrganizerCodeMapper;
import ru.ticketswap.partner.PartnerTicketReissueResponse;
import ru.ticketswap.ticket.TicketLot;
import ru.ticketswap.ticket.TicketRepository;
import ru.ticketswap.ticket.TicketStatus;
import ru.ticketswap.ticket.history.ListingStatusHistoryService;
import ru.ticketswap.user.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private ListingHoldRepository listingHoldRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private TransactionStatus transactionStatus;

    @Mock
    private ListingStatusHistoryService listingStatusHistoryService;

    @Mock
    private PartnerApiClient partnerApiClient;

    @Mock
    private PartnerOrganizerCodeMapper partnerOrganizerCodeMapper;

    private PurchaseService service;
    private User seller;
    private User buyer;
    private TicketLot listing;
    private ListingHold hold;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);

        service = new PurchaseService(
                ticketRepository,
                listingHoldRepository,
                transactionManager,
                listingStatusHistoryService,
                partnerApiClient,
                partnerOrganizerCodeMapper
        );

        seller = createUser(1L, "seller@example.com");
        buyer = createUser(2L, "buyer@example.com");
        listing = createListing("TICKET-12345", "org1");
        listing.setStatus(TicketStatus.PENDING_RECIPIENT);
        hold = new ListingHold(listing, buyer, Instant.now().plusSeconds(300));

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(listingHoldRepository.findByListingId(1L)).thenReturn(Optional.empty());
        when(listingHoldRepository.saveAndFlush(any(ListingHold.class))).thenReturn(hold);
        lenient().when(partnerOrganizerCodeMapper.resolveOrganizerCode("org1")).thenReturn(Optional.of("org1"));
        stubTransitions();
    }

    @Test
    void buyNowReissuesTicketAndCompletesPurchase() {
        when(listingHoldRepository.findByListingIdAndHoldUntilAfter(eq(1L), any(Instant.class)))
                .thenReturn(Optional.of(hold));
        when(partnerApiClient.reissueTicket("org1", "TICKET-12345", "buyer@example.com"))
                .thenReturn(new PartnerTicketReissueResponse(
                        true,
                        "TICKET-12345",
                        "REISSUED-org1-TICKET-12345",
                        "org1",
                        null
                ));

        TicketLot saved = service.buyNow(1L, buyer);

        assertEquals(TicketStatus.COMPLETED, saved.getStatus());
        assertEquals("REISSUED-org1-TICKET-12345", saved.getReissuedTicketUid());
        verify(partnerApiClient).reissueTicket("org1", "TICKET-12345", "buyer@example.com");
        verify(listingStatusHistoryService).recordStatus(
                listing,
                TicketStatus.PROCESSING,
                TicketStatus.PROCESSING,
                "Билет перевыпущен партнёром",
                null
        );
        verify(listingStatusHistoryService).transition(listing, TicketStatus.COMPLETED, "Покупка завершена", buyer);
        verify(listingHoldRepository).deleteByListingId(1L);
    }

    @Test
    void buyNowFailsListingWhenPartnerReturnsBusinessFailure() {
        when(partnerApiClient.reissueTicket("org1", "TICKET-12345", "buyer@example.com"))
                .thenReturn(new PartnerTicketReissueResponse(
                        false,
                        "TICKET-12345",
                        null,
                        "org1",
                        "Mock-перевыпуск не выполнен"
                ));

        TicketLot saved = service.buyNow(1L, buyer);

        assertEquals(TicketStatus.FAILED, saved.getStatus());
        assertNull(saved.getReissuedTicketUid());
        verify(listingStatusHistoryService).transition(
                listing,
                TicketStatus.FAILED,
                "Перевыпуск у партнёра не выполнен: Mock-перевыпуск не выполнен",
                null
        );
        verify(listingHoldRepository).deleteByListingId(1L);
    }

    @Test
    void buyNowFailsListingWhenPartnerIntegrationFails() {
        when(partnerApiClient.reissueTicket("org1", "TICKET-12345", "buyer@example.com"))
                .thenThrow(new PartnerIntegrationException("ошибка"));

        TicketLot saved = service.buyNow(1L, buyer);

        assertEquals(TicketStatus.FAILED, saved.getStatus());
        verify(listingStatusHistoryService).transition(
                listing,
                TicketStatus.FAILED,
                "Перевыпуск у партнёра не выполнен: ошибка интеграции",
                null
        );
        verify(listingHoldRepository).deleteByListingId(1L);
    }

    @Test
    void buyNowFailsListingWithoutPartnerCallWhenOrganizerUnsupported() {
        listing.setOrganizerName("unsupported");
        when(partnerOrganizerCodeMapper.resolveOrganizerCode("unsupported")).thenReturn(Optional.empty());

        TicketLot saved = service.buyNow(1L, buyer);

        assertEquals(TicketStatus.FAILED, saved.getStatus());
        verify(listingStatusHistoryService).transition(
                listing,
                TicketStatus.FAILED,
                "Перевыпуск у партнёра не выполнен: организатор не поддерживается",
                null
        );
        verify(listingHoldRepository).deleteByListingId(1L);
    }

    private void stubTransitions() {
        doAnswer(invocation -> {
            TicketLot lot = invocation.getArgument(0);
            TicketStatus newStatus = invocation.getArgument(1);
            lot.setStatus(newStatus);
            return lot;
        }).when(listingStatusHistoryService).transition(any(TicketLot.class), any(TicketStatus.class), any(String.class), any());
    }

    private TicketLot createListing(String uid, String organizerName) {
        return new TicketLot(
                uid,
                "Концерт",
                LocalDateTime.now().plusDays(10),
                "Арена",
                "Москва",
                BigDecimal.valueOf(4999),
                null,
                organizerName,
                null,
                seller
        );
    }

    private User createUser(Long id, String email) {
        User user = new User(email, "hash");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}