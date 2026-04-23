package ru.ticketswap.ticket.history;

import org.springframework.stereotype.Service;
import ru.ticketswap.ticket.TicketLot;
import ru.ticketswap.ticket.TicketRepository;
import ru.ticketswap.ticket.TicketStatus;
import ru.ticketswap.user.User;

import java.time.Instant;
import java.util.List;

@Service
public class ListingStatusHistoryService {

    private final TicketRepository ticketRepository;
    private final ListingStatusHistoryRepository listingStatusHistoryRepository;

    public ListingStatusHistoryService(
            TicketRepository ticketRepository,
            ListingStatusHistoryRepository listingStatusHistoryRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.listingStatusHistoryRepository = listingStatusHistoryRepository;
    }

    public TicketLot createListingWithInitialStatus(TicketLot listing, String reason, User changedByUser) {
        TicketLot saved = ticketRepository.saveAndFlush(listing);
        recordStatus(saved, null, saved.getStatus(), reason, changedByUser);
        return saved;
    }

    public TicketLot transition(TicketLot listing, TicketStatus newStatus, String reason, User changedByUser) {
        TicketStatus fromStatus = listing.getStatus();
        if (fromStatus == newStatus) {
            return listing;
        }

        listing.setStatus(newStatus);
        TicketLot saved = ticketRepository.saveAndFlush(listing);
        recordStatus(saved, fromStatus, newStatus, reason, changedByUser);
        return saved;
    }

    public void recordStatus(
            TicketLot listing,
            TicketStatus fromStatus,
            TicketStatus toStatus,
            String reason,
            User changedByUser
    ) {
        ListingStatusHistory entry = new ListingStatusHistory(
                listing,
                fromStatus,
                toStatus,
                trimReason(reason),
                changedByUser,
                Instant.now()
        );
        listingStatusHistoryRepository.save(entry);
    }

    public List<ListingStatusHistory> getHistory(Long listingId) {
        return listingStatusHistoryRepository.findAllByListingIdOrderByChangedAtAscIdAsc(listingId);
    }

    private String trimReason(String reason) {
        if (reason == null) {
            return null;
        }

        String trimmed = reason.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.length() <= 255) {
            return trimmed;
        }

        return trimmed.substring(0, 255);
    }
}
