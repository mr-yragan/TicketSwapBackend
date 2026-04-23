package ru.ticketswap.ticket;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.ticketswap.partner.PartnerApiClient;
import ru.ticketswap.partner.PartnerIntegrationException;
import ru.ticketswap.partner.PartnerOrganizerCodeMapper;
import ru.ticketswap.partner.PartnerTicketVerifyResponse;
import ru.ticketswap.ticket.history.ListingStatusHistoryService;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ListingLifecycleService {

    private static final String VALIDATION_STARTED_REASON = "Validation started";
    private static final String VALIDATION_FAILED_PAST_EVENT_REASON = "Validation failed: event date is in the past";
    private static final String PARTNER_VALIDATION_PASSED_REASON = "Partner validation passed";
    private static final String PARTNER_VALIDATION_FAILED_REASON = "Partner validation failed";
    private static final String PARTNER_VALIDATION_UNSUPPORTED_ORGANIZER_REASON = "Partner validation failed: unsupported organizer";
    private static final String PARTNER_VALIDATION_INTEGRATION_ERROR_REASON = "Partner validation failed: integration error";

    private final TicketRepository ticketRepository;
    private final TransactionTemplate tx;
    private final ListingStatusHistoryService listingStatusHistoryService;
    private final PartnerApiClient partnerApiClient;
    private final PartnerOrganizerCodeMapper partnerOrganizerCodeMapper;
    private final Clock clock;

    public ListingLifecycleService(
            TicketRepository ticketRepository,
            PlatformTransactionManager transactionManager,
            ListingStatusHistoryService listingStatusHistoryService,
            PartnerApiClient partnerApiClient,
            PartnerOrganizerCodeMapper partnerOrganizerCodeMapper,
            Clock clock
    ) {
        this.ticketRepository = ticketRepository;
        this.tx = new TransactionTemplate(transactionManager);
        this.listingStatusHistoryService = listingStatusHistoryService;
        this.partnerApiClient = partnerApiClient;
        this.partnerOrganizerCodeMapper = partnerOrganizerCodeMapper;
        this.clock = clock;
    }

    public TicketLot validateListing(Long listingId) {
        if (listingId == null) {
            return null;
        }

        PartnerValidationContext context = tx.execute(status -> startPartnerValidation(listingId));
        if (context == null) {
            return ticketRepository.findById(listingId).orElse(null);
        }

        PartnerValidationOutcome outcome;
        if (context.organizerCode() == null) {
            outcome = PartnerValidationOutcome.failure(PARTNER_VALIDATION_UNSUPPORTED_ORGANIZER_REASON);
        } else {
            outcome = callPartnerApi(context.organizerCode(), context.ticketUid());
        }

        return tx.execute(status -> applyPartnerValidationOutcome(listingId, outcome));
    }

    private PartnerValidationContext startPartnerValidation(Long listingId) {
        TicketLot lot = ticketRepository.findById(listingId).orElse(null);
        if (lot == null) {
            return null;
        }

        if (lot.getStatus() != TicketStatus.CREATED) {
            return null;
        }

        LocalDateTime now = LocalDateTime.now(clock);
        if (lot.getEventDate() != null && lot.getEventDate().isBefore(now)) {
            listingStatusHistoryService.transition(lot, TicketStatus.FAILED, VALIDATION_FAILED_PAST_EVENT_REASON, null);
            return null;
        }

        listingStatusHistoryService.transition(lot, TicketStatus.PENDING_VALIDATION, VALIDATION_STARTED_REASON, null);

        return new PartnerValidationContext(
                lot.getUid(),
                partnerOrganizerCodeMapper.resolveOrganizerCode(lot.getOrganizerName()).orElse(null)
        );
    }

    private PartnerValidationOutcome callPartnerApi(String organizerCode, String ticketUid) {
        try {
            PartnerTicketVerifyResponse response = partnerApiClient.verifyTicket(organizerCode, ticketUid);
            if (response.valid()) {
                return PartnerValidationOutcome.success();
            }

            String reason = response.reason();
            if (reason == null || reason.isBlank()) {
                reason = PARTNER_VALIDATION_FAILED_REASON;
            }
            return PartnerValidationOutcome.failure(reason);
        } catch (PartnerIntegrationException ex) {
            return PartnerValidationOutcome.failure(PARTNER_VALIDATION_INTEGRATION_ERROR_REASON);
        } catch (RuntimeException ex) {
            return PartnerValidationOutcome.failure(PARTNER_VALIDATION_INTEGRATION_ERROR_REASON);
        }
    }

    private TicketLot applyPartnerValidationOutcome(Long listingId, PartnerValidationOutcome outcome) {
        TicketLot lot = ticketRepository.findById(listingId).orElse(null);
        if (lot == null) {
            return null;
        }

        if (lot.getStatus() != TicketStatus.PENDING_VALIDATION) {
            return lot;
        }

        if (outcome.passed()) {
            return listingStatusHistoryService.transition(lot, TicketStatus.PENDING_RECIPIENT, PARTNER_VALIDATION_PASSED_REASON, null);
        }

        return listingStatusHistoryService.transition(lot, TicketStatus.FAILED, outcome.reason(), null);
    }

    private record PartnerValidationContext(String ticketUid, String organizerCode) {
    }

    private record PartnerValidationOutcome(boolean passed, String reason) {

        private static PartnerValidationOutcome success() {
            return new PartnerValidationOutcome(true, PARTNER_VALIDATION_PASSED_REASON);
        }

        private static PartnerValidationOutcome failure(String reason) {
            return new PartnerValidationOutcome(false, reason);
        }
    }
}
