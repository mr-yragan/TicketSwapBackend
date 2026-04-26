package ru.ticketswap.ticket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import ru.ticketswap.partner.PartnerApiClient;
import ru.ticketswap.partner.PartnerOrganizerCodeMapper;
import ru.ticketswap.partner.PartnerIntegrationException;
import ru.ticketswap.partner.PartnerTicketVerifyResponse;
import ru.ticketswap.ticket.history.ListingStatusHistoryService;
import ru.ticketswap.user.User;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingLifecycleServiceTest {

    @Mock
    private TicketRepository ticketRepository;

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

    private ListingLifecycleService service;

    @BeforeEach
    void setUp() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);

        Clock clock = Clock.fixed(Instant.parse("2031-08-20T10:15:30Z"), ZoneOffset.UTC);
        service = new ListingLifecycleService(
                ticketRepository,
                transactionManager,
                listingStatusHistoryService,
                partnerApiClient,
                partnerOrganizerCodeMapper,
                clock
        );
    }

    @Test
    void validateListingRunsValidationAndPartnerVerificationImmediately() {
        TicketLot listing = createListing("org1", LocalDateTime.of(2031, 8, 21, 12, 0));

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(partnerOrganizerCodeMapper.resolveOrganizerCode("org1")).thenReturn(Optional.of("org1"));
        when(partnerApiClient.verifyTicket("org1", "uid-1"))
                .thenReturn(new PartnerTicketVerifyResponse(true, "uid-1", "org1", null));
        stubTransitions();

        TicketLot saved = service.validateListing(1L);

        verify(listingStatusHistoryService).transition(listing, TicketStatus.PENDING_VALIDATION, "Проверка начата", null);
        verify(listingStatusHistoryService).transition(listing, TicketStatus.PENDING_RECIPIENT, "Проверка партнёра пройдена", null);
        verify(partnerApiClient).verifyTicket("org1", "uid-1");
        assertEquals(TicketStatus.PENDING_RECIPIENT, listing.getStatus());
        assertEquals(listing, saved);
    }

    @Test
    void partnerValidationFailsWhenPartnerRespondsWithValidFalse() {
        TicketLot listing = createListing("org1", LocalDateTime.of(2031, 8, 21, 12, 0));

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(partnerOrganizerCodeMapper.resolveOrganizerCode("org1")).thenReturn(Optional.of("org1"));
        when(partnerApiClient.verifyTicket("org1", "uid-1"))
                .thenReturn(new PartnerTicketVerifyResponse(false, "uid-1", "org1", "Билет уже использован"));
        stubTransitions();

        service.validateListing(1L);

        verify(listingStatusHistoryService).transition(listing, TicketStatus.PENDING_VALIDATION, "Проверка начата", null);
        verify(listingStatusHistoryService).transition(listing, TicketStatus.FAILED, "Билет уже использован", null);
        verify(partnerApiClient).verifyTicket("org1", "uid-1");
        assertEquals(TicketStatus.FAILED, listing.getStatus());
    }

    @Test
    void unsupportedOrganizerFailsWithoutPartnerCall() {
        TicketLot listing = createListing("неизвестный организатор", LocalDateTime.of(2031, 8, 21, 12, 0));

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(partnerOrganizerCodeMapper.resolveOrganizerCode("неизвестный организатор")).thenReturn(Optional.empty());
        stubTransitions();

        service.validateListing(1L);

        verify(listingStatusHistoryService).transition(listing, TicketStatus.FAILED, "Проверка партнёра не пройдена: организатор не поддерживается", null);
        verify(partnerApiClient, times(0)).verifyTicket(any(), any());
        assertEquals(TicketStatus.FAILED, listing.getStatus());
    }

    @Test
    void localPastEventValidationRunsBeforePartnerApiCall() {
        TicketLot listing = createListing("org1", LocalDateTime.of(2031, 8, 19, 12, 0));

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(listing));
        stubTransitions();

        service.validateListing(1L);

        verify(listingStatusHistoryService).transition(
                listing,
                TicketStatus.FAILED,
                "Проверка не пройдена: дата мероприятия уже прошла",
                null
        );
        verify(partnerApiClient, never()).verifyTicket(any(), any());
        assertEquals(TicketStatus.FAILED, listing.getStatus());
    }

    @Test
    void partnerIntegrationErrorFailsValidation() {
        TicketLot listing = createListing("org1", LocalDateTime.of(2031, 8, 21, 12, 0));

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(listing));
        when(partnerOrganizerCodeMapper.resolveOrganizerCode("org1")).thenReturn(Optional.of("org1"));
        when(partnerApiClient.verifyTicket("org1", "uid-1"))
                .thenThrow(new PartnerIntegrationException("ошибка"));
        stubTransitions();

        service.validateListing(1L);

        verify(listingStatusHistoryService).transition(listing, TicketStatus.FAILED, "Проверка партнёра не пройдена: ошибка интеграции", null);
        assertEquals(TicketStatus.FAILED, listing.getStatus());
    }

    private void stubTransitions() {
        doAnswer(invocation -> {
            TicketLot lot = invocation.getArgument(0);
            TicketStatus newStatus = invocation.getArgument(1);
            lot.setStatus(newStatus);
            return lot;
        }).when(listingStatusHistoryService).transition(any(TicketLot.class), any(TicketStatus.class), any(String.class), any());
    }

    private TicketLot createListing(String organizerName, LocalDateTime eventDate) {
        User seller = new User("seller@example.com", "hash");
        return new TicketLot(
                "uid-1",
                "Концерт",
                eventDate,
                "Арена",
                "Берлин",
                BigDecimal.valueOf(150),
                null,
                organizerName,
                null,
                seller
        );
    }
}