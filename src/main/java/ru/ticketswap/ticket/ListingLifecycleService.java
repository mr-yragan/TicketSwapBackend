package ru.ticketswap.ticket;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerVerificationMode;
import ru.ticketswap.partner.PartnerApiClient;
import ru.ticketswap.partner.PartnerIntegrationException;
import ru.ticketswap.partner.PartnerOrganizerCodeMapper;
import ru.ticketswap.partner.PartnerTicketVerifyResponse;
import ru.ticketswap.ticket.history.ListingStatusHistoryService;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ListingLifecycleService {

    private static final String VALIDATION_STARTED_REASON = "Проверка начата";
    private static final String VALIDATION_FAILED_PAST_EVENT_REASON = "Проверка не пройдена: дата мероприятия уже прошла";
    private static final String PARTNER_VALIDATION_PASSED_REASON = "Проверка партнёра пройдена";
    private static final String PARTNER_VALIDATION_FAILED_REASON = "Проверка партнёра не пройдена";
    private static final String PARTNER_VALIDATION_UNSUPPORTED_ORGANIZER_REASON = "Проверка партнёра не пройдена: организатор не поддерживается";
    private static final String PARTNER_VALIDATION_INTEGRATION_ERROR_REASON = "Проверка партнёра не пройдена: ошибка интеграции";
    private static final String MANUAL_VALIDATION_WAITING_REASON = "Ожидает ручной проверки организатором";
    private static final String ORGANIZER_BANNED_REASON = "Проверка не пройдена: организатор заблокирован";

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

        ValidationContext context = tx.execute(status -> startValidation(listingId));
        if (context == null) {
            return ticketRepository.findById(listingId).orElse(null);
        }

        if (context.manual()) {
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

    private ValidationContext startValidation(Long listingId) {
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

        Organizer organizer = lot.getOrganizer();
        if (organizer == null) {
            listingStatusHistoryService.transition(lot, TicketStatus.FAILED, "Проверка не пройдена: организатор не выбран", null);
            return null;
        }

        if (organizer.isBanned()) {
            listingStatusHistoryService.transition(lot, TicketStatus.FAILED, ORGANIZER_BANNED_REASON, null);
            return null;
        }

        listingStatusHistoryService.transition(lot, TicketStatus.PENDING_VALIDATION, VALIDATION_STARTED_REASON, null);

        if (organizer.getVerificationMode() == OrganizerVerificationMode.MANUAL) {
            listingStatusHistoryService.recordStatus(
                    lot,
                    TicketStatus.PENDING_VALIDATION,
                    TicketStatus.PENDING_VALIDATION,
                    MANUAL_VALIDATION_WAITING_REASON,
                    null
            );
            return new ValidationContext(lot.getUid(), null, true);
        }

        String organizerCode = organizer.getApiKey();
        if (organizerCode == null || organizerCode.isBlank()) {
            organizerCode = partnerOrganizerCodeMapper.resolveOrganizerCode(lot.getOrganizerName()).orElse(null);
        }

        return new ValidationContext(lot.getUid(), organizerCode, false);
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

    private record ValidationContext(String ticketUid, String organizerCode, boolean manual) {
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
