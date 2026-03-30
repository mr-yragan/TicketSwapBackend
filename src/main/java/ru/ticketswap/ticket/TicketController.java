package ru.ticketswap.ticket;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.ticketswap.common.BusinessRuleException;
import ru.ticketswap.common.NotFoundException;
import ru.ticketswap.common.UnauthorizedException;
import ru.ticketswap.hold.ListingHold;
import ru.ticketswap.hold.ListingHoldRepository;
import ru.ticketswap.hold.dto.ListingHoldResponse;
import ru.ticketswap.purchase.PurchaseService;
import ru.ticketswap.storage.TicketFileStorageService;
import ru.ticketswap.ticket.dto.CreateTicketRequest;
import ru.ticketswap.ticket.dto.ListingDetailsResponse;
import ru.ticketswap.ticket.dto.ListingViewResponse;
import ru.ticketswap.ticket.dto.TicketFileDownloadUrlResponse;
import ru.ticketswap.ticket.dto.TicketLotResponse;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final ListingHoldRepository listingHoldRepository;
    private final PurchaseService purchaseService;
    private final ListingLifecycleService listingLifecycleService;
    private final TicketFileStorageService ticketFileStorageService;

    public TicketController(
            TicketRepository ticketRepository,
            UserRepository userRepository,
            ListingHoldRepository listingHoldRepository,
            PurchaseService purchaseService,
            ListingLifecycleService listingLifecycleService,
            TicketFileStorageService ticketFileStorageService
    ) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.listingHoldRepository = listingHoldRepository;
        this.purchaseService = purchaseService;
        this.listingLifecycleService = listingLifecycleService;
        this.ticketFileStorageService = ticketFileStorageService;
    }

    @GetMapping
    public ResponseEntity<List<TicketLotResponse>> listTickets() {
        LocalDateTime now = LocalDateTime.now();
        Set<Long> heldIds = new HashSet<>(listingHoldRepository.findActiveListingIds(Instant.now()));

        List<TicketLotResponse> response = ticketRepository.findAll().stream()
                .filter(t -> t.getStatus() == TicketStatus.PENDING_RECIPIENT)
                .filter(t -> t.getEventDate() != null && t.getEventDate().isAfter(now))
                .filter(t -> !heldIds.contains(t.getId()))
                .sorted(Comparator.comparing(TicketLot::getCreatedAt).reversed())
                .map(this::toTicketLotResponse)
                .toList();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ListingViewResponse> getListing(@PathVariable("id") Long id) {
        TicketLot ticket = loadTicket(id);

        Optional<ListingHold> activeHold = listingHoldRepository.findByListingIdAndHoldUntilAfter(id, Instant.now());
        ListingViewResponse.Hold hold = activeHold
                .map(h -> new ListingViewResponse.Hold(h.getId(), h.getHoldUntil()))
                .orElse(null);

        ListingViewResponse response = new ListingViewResponse(
                toDetailsResponse(ticket),
                ticket.getStatus(),
                hold
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/sell", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ListingDetailsResponse> sellTicket(
            @Valid @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User seller = requireUser(userDetails);
        TicketLot saved = createListing(request, seller);
        listingLifecycleService.onListingCreated(saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDetailsResponse(saved));
    }

    @PostMapping(value = "/sell", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ListingDetailsResponse> sellTicketWithFile(
            @Valid @RequestPart("ticket") CreateTicketRequest request,
            @RequestPart("ticketFile") MultipartFile ticketFile,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User seller = requireUser(userDetails);
        TicketLot saved = createListing(request, seller);

        try {
            saved = ticketFileStorageService.uploadTicketFile(saved, ticketFile);
            listingLifecycleService.onListingCreated(saved.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(toDetailsResponse(saved));
        } catch (RuntimeException ex) {
            ticketRepository.deleteById(saved.getId());
            throw ex;
        }
    }

    @PostMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ListingDetailsResponse> uploadTicketFile(
            @PathVariable("id") Long id,
            @RequestPart("ticketFile") MultipartFile ticketFile,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User seller = requireUser(userDetails);
        TicketLot ticket = loadTicket(id);
        ensureSellerCanModifyFile(ticket, seller);
        TicketLot saved = ticketFileStorageService.uploadTicketFile(ticket, ticketFile);
        return ResponseEntity.ok(toDetailsResponse(saved));
    }

    @GetMapping("/{id}/file/download-url")
    public ResponseEntity<TicketFileDownloadUrlResponse> getTicketFileDownloadUrl(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User currentUser = requireUser(userDetails);
        TicketLot ticket = loadTicket(id);
        ensureCanReadTicketFile(ticket, currentUser);
        return ResponseEntity.ok(ticketFileStorageService.createDownloadUrl(ticket));
    }

    @DeleteMapping("/{id}/file")
    public ResponseEntity<Void> deleteTicketFile(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User seller = requireUser(userDetails);
        TicketLot ticket = loadTicket(id);
        ensureSellerCanModifyFile(ticket, seller);
        ticketFileStorageService.deleteTicketFile(ticket);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/hold")
    public ResponseEntity<ListingHoldResponse> holdListing(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User buyer = requireUser(userDetails);

        ListingHold hold = purchaseService.createHold(id, buyer);

        TicketLot listing = loadTicket(id);

        ListingHoldResponse response = new ListingHoldResponse(
                hold.getId(),
                TicketLotResponse.fromEntity(listing),
                hold.getHoldUntil(),
                hold.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}/hold")
    public ResponseEntity<Void> cancelHold(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User buyer = requireUser(userDetails);
        purchaseService.cancelHold(id, buyer);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/buy")
    public ResponseEntity<ListingDetailsResponse> buyTicket(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User buyer = requireUser(userDetails);
        TicketLot saved = purchaseService.buyNow(id, buyer);
        return ResponseEntity.ok(toDetailsResponse(saved));
    }

    @GetMapping("/my")
    public ResponseEntity<List<TicketLotResponse>> myTickets(@AuthenticationPrincipal UserDetails userDetails) {
        User user = requireUser(userDetails);
        List<TicketLotResponse> response = ticketRepository.findAllBySellerIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(this::toTicketLotResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    private TicketLot createListing(CreateTicketRequest request, User seller) {
        VenueParts venueParts = parseVenueParts(request.venue());

        TicketLot ticket = new TicketLot(
                request.uid(),
                request.eventName(),
                request.eventDate(),
                venueParts.venueName(),
                venueParts.venueCity(),
                request.price(),
                request.additionalInfo(),
                request.organizerName(),
                request.sellerComment(),
                seller
        );

        return ticketRepository.saveAndFlush(ticket);
    }

    private TicketLot loadTicket(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
    }

    private User requireUser(UserDetails principal) {
        if (principal == null || principal.getUsername() == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
    }

    private void ensureSellerCanModifyFile(TicketLot ticket, User seller) {
        if (ticket.getSeller() == null || ticket.getSeller().getId() == null || !ticket.getSeller().getId().equals(seller.getId())) {
            throw new UnauthorizedException("You can modify only your own ticket file");
        }
        if (ticket.getStatus() == TicketStatus.PROCESSING || ticket.getStatus() == TicketStatus.COMPLETED) {
            throw new BusinessRuleException("Ticket file cannot be changed after purchase has started");
        }
    }

    private void ensureCanReadTicketFile(TicketLot ticket, User currentUser) {
        boolean isSeller = ticket.getSeller() != null
                && ticket.getSeller().getId() != null
                && ticket.getSeller().getId().equals(currentUser.getId());

        boolean isCompletedBuyer = ticket.getStatus() == TicketStatus.COMPLETED
                && ticket.getBuyer() != null
                && ticket.getBuyer().getId() != null
                && ticket.getBuyer().getId().equals(currentUser.getId());

        if (!isSeller && !isCompletedBuyer) {
            throw new UnauthorizedException("You do not have access to this ticket file");
        }
    }

    private TicketLotResponse toTicketLotResponse(TicketLot ticket) {
        return new TicketLotResponse(
                ticket.getId(),
                ticket.getEventName(),
                ticket.getEventDate(),
                formatVenue(ticket.getVenueName(), ticket.getVenueCity()),
                ticket.getResalePrice(),
                isVerified(ticket)
        );
    }

    private ListingDetailsResponse toDetailsResponse(TicketLot ticket) {
        ListingDetailsResponse.SellerInfo sellerInfo = null;
        if (ticket.getSeller() != null) {
            String displayName = ticket.getSeller().getLogin();
            if (displayName == null || displayName.isBlank()) {
                displayName = ticket.getSeller().getEmail();
            }
            sellerInfo = new ListingDetailsResponse.SellerInfo(displayName, ticket.getSeller().getCreatedAt());
        }

        return new ListingDetailsResponse(
                ticket.getId(),
                ticket.getEventName(),
                ticket.getEventDate(),
                formatVenue(ticket.getVenueName(), ticket.getVenueCity()),
                ticket.getResalePrice(),
                isVerified(ticket),
                ticket.getAdditionalInfo(),
                ticket.getOrganizerName(),
                ticket.getSellerComment(),
                sellerInfo,
                ticket.hasTicketFile()
        );
    }

    private boolean isVerified(TicketLot ticket) {
        TicketStatus status = ticket.getStatus();
        if (status == null) {
            return false;
        }
        return status == TicketStatus.PENDING_RECIPIENT
                || status == TicketStatus.PROCESSING
                || status == TicketStatus.COMPLETED;
    }

    private record VenueParts(String venueName, String venueCity) {
    }

    private VenueParts parseVenueParts(String venueLine) {
        if (venueLine == null) {
            return new VenueParts("", "");
        }
        String trimmed = venueLine.trim();
        if (trimmed.isEmpty()) {
            return new VenueParts("", "");
        }
        int comma = trimmed.indexOf(',');
        if (comma < 0) {
            return new VenueParts(trimmed, "");
        }
        String name = trimmed.substring(0, comma).trim();
        String city = trimmed.substring(comma + 1).trim();
        return new VenueParts(name, city);
    }

    private String formatVenue(String venueName, String venueCity) {
        String name = venueName == null ? "" : venueName.trim();
        String city = venueCity == null ? "" : venueCity.trim();
        if (name.isEmpty() && city.isEmpty()) {
            return "";
        }
        if (city.isEmpty()) {
            return name;
        }
        if (name.isEmpty()) {
            return city;
        }
        return name + ", " + city;
    }
}
