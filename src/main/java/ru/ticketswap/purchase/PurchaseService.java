package ru.ticketswap.purchase;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.ticketswap.common.BusinessRuleException;
import ru.ticketswap.common.ConflictException;
import ru.ticketswap.common.NotFoundException;
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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
public class PurchaseService {

    private static final long DEFAULT_HOLD_SECONDS = 5 * 60;
    private static final long MOCK_PAYMENT_DELAY_MS = 2_000;

    private final TicketRepository ticketRepository;
    private final ListingHoldRepository listingHoldRepository;
    private final TransactionTemplate tx;
    private final ListingStatusHistoryService listingStatusHistoryService;
    private final PartnerApiClient partnerApiClient;
    private final PartnerOrganizerCodeMapper partnerOrganizerCodeMapper;

    public PurchaseService(
            TicketRepository ticketRepository,
            ListingHoldRepository listingHoldRepository,
            PlatformTransactionManager transactionManager,
            ListingStatusHistoryService listingStatusHistoryService,
            PartnerApiClient partnerApiClient,
            PartnerOrganizerCodeMapper partnerOrganizerCodeMapper
    ) {
        this.ticketRepository = ticketRepository;
        this.listingHoldRepository = listingHoldRepository;
        this.tx = new TransactionTemplate(transactionManager);
        this.listingStatusHistoryService = listingStatusHistoryService;
        this.partnerApiClient = partnerApiClient;
        this.partnerOrganizerCodeMapper = partnerOrganizerCodeMapper;
    }

    public ListingHold createHold(Long listingId, User buyer) {
        return tx.execute(status -> createHoldTx(listingId, buyer));
    }

    public void cancelHold(Long listingId, User buyer) {
        tx.executeWithoutResult(status -> cancelHoldTx(listingId, buyer));
    }

    public TicketLot buyNow(Long listingId, User buyer) {
        tx.executeWithoutResult(status -> startProcessingTx(listingId, buyer));

        try {
            Thread.sleep(MOCK_PAYMENT_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessRuleException("Payment was interrupted");
        }

        ReissueResult reissueResult = reissueTicketWithPartner(listingId, buyer);
        if (!reissueResult.success()) {
            return tx.execute(status -> failPurchaseTx(listingId, buyer, reissueResult.failureReason()));
        }

        return tx.execute(status -> completePurchaseTx(listingId, buyer, reissueResult.reissuedTicketUid()));
    }

    private ListingHold createHoldTx(Long listingId, User buyer) {
        TicketLot listing = loadListingForPurchase(listingId, buyer);

        Instant now = Instant.now();
        Optional<ListingHold> existing = listingHoldRepository.findByListingId(listingId);
        if (existing.isPresent()) {
            ListingHold hold = existing.get();
            boolean active = hold.getHoldUntil() != null && hold.getHoldUntil().isAfter(now);

            if (active) {
                if (hold.getBuyer() != null && hold.getBuyer().getId() != null && hold.getBuyer().getId().equals(buyer.getId())) {
                    return hold;
                }
                throw new ConflictException("Ticket is reserved by another buyer");
            }

            listingHoldRepository.delete(hold);
            listingHoldRepository.flush();
        }

        Instant holdUntil = now.plusSeconds(DEFAULT_HOLD_SECONDS);
        try {
            ListingHold created = new ListingHold(listing, buyer, holdUntil);
            return listingHoldRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Ticket is reserved by another buyer");
        }
    }

    private void cancelHoldTx(Long listingId, User buyer) {
        Optional<ListingHold> existing = listingHoldRepository.findByListingId(listingId);
        if (existing.isEmpty()) {
            return;
        }

        ListingHold hold = existing.get();
        if (hold.getBuyer() == null || hold.getBuyer().getId() == null || !hold.getBuyer().getId().equals(buyer.getId())) {
            throw new ConflictException("You cannot cancel a hold created by another user");
        }

        listingHoldRepository.delete(hold);
    }

    private void startProcessingTx(Long listingId, User buyer) {
        TicketLot listing = loadListingForPurchase(listingId, buyer);

        ListingHold hold = createHoldTx(listingId, buyer);
        Instant now = Instant.now();
        if (hold.getHoldUntil() == null || !hold.getHoldUntil().isAfter(now)) {
            throw new ConflictException("Hold is expired");
        }

        if (listing.getStatus() == TicketStatus.PROCESSING) {
            if (listing.getBuyer() != null && listing.getBuyer().getId() != null && listing.getBuyer().getId().equals(buyer.getId())) {
                return;
            }
            throw new ConflictException("Ticket is already being purchased");
        }

        listing.setBuyer(buyer);
        listingStatusHistoryService.transition(listing, TicketStatus.PROCESSING, "Purchase started", buyer);
    }

    private TicketLot completePurchaseTx(Long listingId, User buyer, String reissuedTicketUid) {
        TicketLot listing = ticketRepository.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));

        if (listing.getStatus() == TicketStatus.COMPLETED) {
            return listing;
        }

        ListingHold hold = listingHoldRepository.findByListingIdAndHoldUntilAfter(listingId, Instant.now())
                .orElseThrow(() -> new ConflictException("No active hold for this listing"));

        if (hold.getBuyer() == null || hold.getBuyer().getId() == null || !hold.getBuyer().getId().equals(buyer.getId())) {
            throw new ConflictException("This listing is held by another buyer");
        }

        if (listing.getBuyer() == null || listing.getBuyer().getId() == null || !listing.getBuyer().getId().equals(buyer.getId())) {
            listing.setBuyer(buyer);
        }

        listing.setReissuedTicketUid(reissuedTicketUid);
        listingStatusHistoryService.recordStatus(listing, listing.getStatus(), listing.getStatus(), "Ticket reissued by partner", null);
        TicketLot saved = listingStatusHistoryService.transition(listing, TicketStatus.COMPLETED, "Purchase completed", buyer);

        listingHoldRepository.deleteByListingId(listingId);

        return saved;
    }

    private TicketLot failPurchaseTx(Long listingId, User buyer, String reason) {
        TicketLot listing = ticketRepository.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));

        if (listing.getBuyer() == null || listing.getBuyer().getId() == null || !listing.getBuyer().getId().equals(buyer.getId())) {
            listing.setBuyer(buyer);
        }

        TicketLot saved = listingStatusHistoryService.transition(listing, TicketStatus.FAILED, reason, null);
        listingHoldRepository.deleteByListingId(listingId);
        return saved;
    }

    private ReissueResult reissueTicketWithPartner(Long listingId, User buyer) {
        TicketLot listing = ticketRepository.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));

        Optional<String> organizerCode = partnerOrganizerCodeMapper.resolveOrganizerCode(listing.getOrganizerName());
        if (organizerCode.isEmpty()) {
            return ReissueResult.failed("Partner reissue failed: unsupported organizer");
        }

        try {
            PartnerTicketReissueResponse response = partnerApiClient.reissueTicket(
                    organizerCode.get(),
                    listing.getUid(),
                    buyer.getEmail()
            );

            if (!response.success()) {
                return ReissueResult.failed("Partner reissue failed: " + failureReason(response.reason()));
            }

            return ReissueResult.success(response.newTicketUid());
        } catch (PartnerIntegrationException ex) {
            return ReissueResult.failed("Partner reissue failed: integration error");
        }
    }

    private String failureReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unknown reason";
        }
        return reason.trim();
    }

    private TicketLot loadListingForPurchase(Long listingId, User buyer) {
        TicketLot listing = ticketRepository.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));

        if (listing.getStatus() == TicketStatus.COMPLETED) {
            throw new BusinessRuleException("Ticket is already sold");
        }

        if (listing.getStatus() == TicketStatus.PROCESSING) {
            if (listing.getBuyer() != null && listing.getBuyer().getId() != null && listing.getBuyer().getId().equals(buyer.getId())) {
                return listing;
            }
            throw new ConflictException("Ticket is already being purchased");
        }

        if (listing.getStatus() != TicketStatus.PENDING_RECIPIENT) {
            throw new BusinessRuleException("Ticket is not available for purchase");
        }

        if (listing.getSeller() != null && listing.getSeller().getId() != null && listing.getSeller().getId().equals(buyer.getId())) {
            throw new BusinessRuleException("You cannot buy your own ticket");
        }

        LocalDateTime now = LocalDateTime.now();
        if (listing.getEventDate() != null && listing.getEventDate().isBefore(now)) {
            throw new BusinessRuleException("Event has already happened");
        }

        return listing;
    }

    private record ReissueResult(
            boolean success,
            String reissuedTicketUid,
            String failureReason
    ) {

        private static ReissueResult success(String reissuedTicketUid) {
            return new ReissueResult(true, reissuedTicketUid, null);
        }

        private static ReissueResult failed(String failureReason) {
            return new ReissueResult(false, null, failureReason);
        }
    }
}
