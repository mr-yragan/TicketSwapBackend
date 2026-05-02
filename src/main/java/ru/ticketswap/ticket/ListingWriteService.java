package ru.ticketswap.ticket;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import ru.ticketswap.audit.AuditLogService;
import ru.ticketswap.ticket.history.ListingStatusHistoryService;
import ru.ticketswap.user.User;

@Service
public class ListingWriteService {

    public static final String REVALIDATION_REASON = "Объявление изменено и отправлено на повторную проверку";

    private final TicketRepository ticketRepository;
    private final ListingStatusHistoryService listingStatusHistoryService;
    private final AuditLogService auditLogService;

    public ListingWriteService(
            TicketRepository ticketRepository,
            ListingStatusHistoryService listingStatusHistoryService,
            AuditLogService auditLogService
    ) {
        this.ticketRepository = ticketRepository;
        this.listingStatusHistoryService = listingStatusHistoryService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public TicketLot prepareForRevalidation(TicketLot listing, User changedByUser) {
        TicketStatus previousStatus = listing.getStatus();

        listing.setBuyer(null);
        listing.setStatus(TicketStatus.CREATED);

        TicketLot saved = ticketRepository.saveAndFlush(listing);
        listingStatusHistoryService.recordStatus(
                saved,
                previousStatus,
                TicketStatus.CREATED,
                REVALIDATION_REASON,
                changedByUser
        );
        auditLogService.record(
                changedByUser,
                "TICKET_REVALIDATION_REQUESTED",
                "TICKET_LOT",
                saved.getId(),
                "listingId=" + saved.getId() + "; previousStatus=" + previousStatus + "; uid=" + saved.getUid()
        );

        return saved;
    }
}
