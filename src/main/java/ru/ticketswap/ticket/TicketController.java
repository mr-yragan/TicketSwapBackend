package ru.ticketswap.ticket;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.ticketswap.common.BusinessRuleException;
import ru.ticketswap.event.Event;
import ru.ticketswap.event.EventRepository;
import ru.ticketswap.common.NotFoundException;
import ru.ticketswap.common.UnauthorizedException;
import ru.ticketswap.hold.ListingHold;
import ru.ticketswap.hold.ListingHoldRepository;
import ru.ticketswap.hold.dto.ListingHoldResponse;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerRepository;
import ru.ticketswap.partner.PartnerOrganizerCodeMapper;
import ru.ticketswap.purchase.PurchaseService;
import ru.ticketswap.storage.TicketFileStorageService;
import ru.ticketswap.ticket.history.ListingStatusHistoryService;
import ru.ticketswap.ticket.dto.CreateTicketRequest;
import ru.ticketswap.ticket.dto.ListingDetailsResponse;
import ru.ticketswap.ticket.dto.ListingStatusHistoryResponse;
import ru.ticketswap.ticket.dto.ListingViewResponse;
import ru.ticketswap.ticket.dto.TicketFileDownloadUrlResponse;
import ru.ticketswap.ticket.dto.TicketFilesResponse;
import ru.ticketswap.ticket.dto.TicketLotResponse;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final ListingWriteService listingWriteService;
    private final PartnerOrganizerCodeMapper partnerOrganizerCodeMapper;
    private final OrganizerRepository organizerRepository;
    private final EventRepository eventRepository;
    private final TicketFileStorageService ticketFileStorageService;
    private final ListingStatusHistoryService listingStatusHistoryService;

    @Autowired
    public TicketController(
            TicketRepository ticketRepository,
            UserRepository userRepository,
            ListingHoldRepository listingHoldRepository,
            PurchaseService purchaseService,
            ListingLifecycleService listingLifecycleService,
            ListingWriteService listingWriteService,
            PartnerOrganizerCodeMapper partnerOrganizerCodeMapper,
            OrganizerRepository organizerRepository,
            EventRepository eventRepository,
            TicketFileStorageService ticketFileStorageService,
            ListingStatusHistoryService listingStatusHistoryService
    ) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.listingHoldRepository = listingHoldRepository;
        this.purchaseService = purchaseService;
        this.listingLifecycleService = listingLifecycleService;
        this.listingWriteService = listingWriteService;
        this.partnerOrganizerCodeMapper = partnerOrganizerCodeMapper;
        this.organizerRepository = organizerRepository;
        this.eventRepository = eventRepository;
        this.ticketFileStorageService = ticketFileStorageService;
        this.listingStatusHistoryService = listingStatusHistoryService;
    }

    public TicketController(
            TicketRepository ticketRepository,
            UserRepository userRepository,
            ListingHoldRepository listingHoldRepository,
            PurchaseService purchaseService,
            ListingLifecycleService listingLifecycleService,
            ListingWriteService listingWriteService,
            PartnerOrganizerCodeMapper partnerOrganizerCodeMapper,
            EventRepository eventRepository,
            TicketFileStorageService ticketFileStorageService,
            ListingStatusHistoryService listingStatusHistoryService
    ) {
        this(
                ticketRepository,
                userRepository,
                listingHoldRepository,
                purchaseService,
                listingLifecycleService,
                listingWriteService,
                partnerOrganizerCodeMapper,
                null,
                eventRepository,
                ticketFileStorageService,
                listingStatusHistoryService
        );
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
    public ResponseEntity<ListingViewResponse> getListing(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        TicketLot ticket = loadTicket(id);
        User currentUser = tryLoadUser(userDetails);

        if (!isVisibleForPublic(ticket) && !isSeller(ticket, currentUser) && !isBuyer(ticket, currentUser) && !isOrganizerForTicket(ticket, currentUser)) {
            throw new NotFoundException("Билет не найден");
        }

        Optional<ListingHold> activeHold = listingHoldRepository.findByListingIdAndHoldUntilAfter(id, Instant.now());
        ListingViewResponse.Hold hold = activeHold
                .map(h -> new ListingViewResponse.Hold(h.getId(), h.getHoldUntil()))
                .orElse(null);

        ListingViewResponse response = new ListingViewResponse(
                toDetailsResponse(ticket, currentUser),
                ticket.getStatus(),
                hold
        );

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/status-history")
    public ResponseEntity<List<ListingStatusHistoryResponse>> getStatusHistory(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        TicketLot ticket = loadTicket(id);
        User currentUser = tryLoadUser(userDetails);

        if (!isVisibleForPublic(ticket) && !isSeller(ticket, currentUser) && !isBuyer(ticket, currentUser) && !isOrganizerForTicket(ticket, currentUser)) {
            throw new NotFoundException("Билет не найден");
        }

        List<ListingStatusHistoryResponse> response = listingStatusHistoryService.getHistory(id).stream()
                .map(ListingStatusHistoryResponse::fromEntity)
                .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/sell", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ListingDetailsResponse> sellTicket(
            @Valid @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User seller = requireUser(userDetails);
        TicketLot saved = createListing(request, seller);
        saved = listingLifecycleService.validateListing(saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDetailsResponse(saved));
    }

    @PostMapping(value = "/sell", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ListingDetailsResponse> sellTicketWithFiles(
            @Valid @RequestPart("ticket") CreateTicketRequest request,
            @RequestPart(value = "ticketFiles", required = false) List<MultipartFile> ticketFiles,
            @RequestPart(value = "ticketFile", required = false) MultipartFile singleTicketFile,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User seller = requireUser(userDetails);
        TicketLot saved = createListing(request, seller);
        List<MultipartFile> files = collectFiles(ticketFiles, singleTicketFile);

        try {
            if (!files.isEmpty()) {
                ticketFileStorageService.uploadTicketFiles(saved, files);
                saved = loadTicket(saved.getId());
            }

            saved = listingLifecycleService.validateListing(saved.getId());
            return ResponseEntity.status(HttpStatus.CREATED).body(toDetailsResponse(saved));
        } catch (RuntimeException ex) {
            ticketFileStorageService.deleteFilesQuietly(saved);
            ticketRepository.deleteById(saved.getId());
            throw ex;
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ListingDetailsResponse> updateListing(
            @PathVariable("id") Long id,
            @Valid @RequestBody CreateTicketRequest request,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User seller = requireUser(userDetails);
        TicketLot ticket = loadTicket(id);

        ensureSellerCanModifyListing(ticket, seller);

        VenueParts newVenueParts = parseVenueParts(request.venue());
        boolean requiresRevalidation = requiresRevalidation(ticket, request, newVenueParts);

        applyEditableFields(ticket, request, newVenueParts);

        TicketLot saved;
        if (requiresRevalidation) {
            saved = listingWriteService.prepareForRevalidation(ticket, seller);
            saved = listingLifecycleService.validateListing(saved.getId());
        } else {
            saved = ticketRepository.saveAndFlush(ticket);
        }

        return ResponseEntity.ok(toDetailsResponse(saved, seller));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancelListing(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User seller = requireUser(userDetails);
        TicketLot ticket = loadTicket(id);

        ensureSellerCanModifyListing(ticket, seller);

        listingHoldRepository.deleteByListingId(id);
        listingHoldRepository.flush();

        ticket.setBuyer(null);
        listingStatusHistoryService.transition(ticket, TicketStatus.FAILED, "Объявление отменено продавцом", seller);

        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ListingDetailsResponse> uploadSingleTicketFile(
            @PathVariable("id") Long id,
            @RequestPart("ticketFile") MultipartFile ticketFile,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User seller = requireUser(userDetails);
        TicketLot ticket = loadTicket(id);
        ensureSellerCanModifyFile(ticket, seller);
        ticketFileStorageService.uploadTicketFiles(ticket, List.of(ticketFile));
        return ResponseEntity.ok(toDetailsResponse(loadTicket(id)));
    }

    @PostMapping(value = "/{id}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TicketFilesResponse> uploadTicketFiles(
            @PathVariable("id") Long id,
            @RequestPart(value = "ticketFiles", required = false) List<MultipartFile> ticketFiles,
            @RequestPart(value = "ticketFile", required = false) MultipartFile singleTicketFile,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User seller = requireUser(userDetails);
        TicketLot ticket = loadTicket(id);
        ensureSellerCanModifyFile(ticket, seller);
        TicketFilesResponse response = ticketFileStorageService.uploadTicketFiles(ticket, collectFiles(ticketFiles, singleTicketFile));
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/files")
    public ResponseEntity<TicketFilesResponse> listTicketFiles(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User currentUser = requireUser(userDetails);
        TicketLot ticket = loadTicket(id);
        ensureCanReadTicketFiles(ticket, currentUser);
        return ResponseEntity.ok(ticketFileStorageService.listFiles(ticket));
    }

    @GetMapping("/{id}/file/download-url")
    public ResponseEntity<TicketFileDownloadUrlResponse> getSingleTicketFileDownloadUrl(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User currentUser = requireUser(userDetails);
        TicketLot ticket = loadTicket(id);
        ensureCanReadTicketFiles(ticket, currentUser);
        return ResponseEntity.ok(ticketFileStorageService.createSingleDownloadUrl(ticket));
    }

    @GetMapping("/{id}/files/{fileId}/download-url")
    public ResponseEntity<TicketFileDownloadUrlResponse> getTicketFileDownloadUrl(
            @PathVariable("id") Long id,
            @PathVariable("fileId") Long fileId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User currentUser = requireUser(userDetails);
        TicketLot ticket = loadTicket(id);
        ensureCanReadTicketFiles(ticket, currentUser);
        return ResponseEntity.ok(ticketFileStorageService.createDownloadUrl(ticket, fileId));
    }

    @GetMapping("/{id}/reissued-file/download-url")
    public ResponseEntity<TicketFileDownloadUrlResponse> getReissuedTicketFileDownloadUrl(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User currentUser = requireUser(userDetails);
        TicketLot ticket = loadTicket(id);
        ensureCanReadReissuedTicket(ticket, currentUser);
        return ResponseEntity.ok(ticketFileStorageService.createReissuedTicketDownloadUrl(ticket));
    }

    @DeleteMapping("/{id}/file")
    public ResponseEntity<Void> deleteAllTicketFilesCompatibility(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User seller = requireUser(userDetails);
        TicketLot ticket = loadTicket(id);
        ensureSellerCanModifyFile(ticket, seller);
        ticketFileStorageService.deleteAllTicketFiles(ticket);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/files")
    public ResponseEntity<Void> deleteAllTicketFiles(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User seller = requireUser(userDetails);
        TicketLot ticket = loadTicket(id);
        ensureSellerCanModifyFile(ticket, seller);
        ticketFileStorageService.deleteAllTicketFiles(ticket);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/files/{fileId}")
    public ResponseEntity<Void> deleteTicketFile(
            @PathVariable("id") Long id,
            @PathVariable("fileId") Long fileId,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User seller = requireUser(userDetails);
        TicketLot ticket = loadTicket(id);
        ensureSellerCanModifyFile(ticket, seller);
        ticketFileStorageService.deleteTicketFile(ticket, fileId);
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
        return ResponseEntity.ok(toDetailsResponse(saved, buyer));
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
        Organizer organizer = resolveOrganizer(request);
        Event linkedEvent = resolveLinkedEvent(request, organizer);

        TicketLot ticket = new TicketLot(
                request.uid(),
                request.eventName(),
                request.eventDate(),
                venueParts.venueName(),
                venueParts.venueCity(),
                request.price(),
                request.additionalInfo(),
                organizer.getName(),
                request.sellerComment(),
                seller
        );
        ticket.setOrganizer(organizer);
        ticket.setEvent(linkedEvent);

        return listingStatusHistoryService.createListingWithInitialStatus(ticket, "Объявление создано", seller);
    }

    private Organizer resolveOrganizer(CreateTicketRequest request) {
        if (organizerRepository == null) {
            String organizerName = request.organizerName();
            if (organizerName == null || organizerName.isBlank()) {
                throw new BusinessRuleException("Нужно выбрать зарегистрированного организатора");
            }
            String normalized = organizerName.trim();
            return new Organizer(normalized, normalized, "compat-organizer@example.invalid");
        }

        if (request.organizerId() != null) {
            Organizer organizer = organizerRepository.findById(request.organizerId())
                    .orElseThrow(() -> new BusinessRuleException("Организатор не зарегистрирован"));
            ensureOrganizerCanAcceptListings(organizer);
            return organizer;
        }

        String organizerName = request.organizerName();
        if (organizerName == null || organizerName.isBlank()) {
            throw new BusinessRuleException("Нужно выбрать зарегистрированного организатора");
        }

        String normalized = organizerName.trim();
        Organizer organizer = organizerRepository.findByApiKeyIgnoreCase(normalized)
                .or(() -> organizerRepository.findByNameIgnoreCase(normalized))
                .orElseThrow(() -> new BusinessRuleException("Организатор не зарегистрирован"));
        ensureOrganizerCanAcceptListings(organizer);
        return organizer;
    }

    private void ensureOrganizerCanAcceptListings(Organizer organizer) {
        if (organizer.isBanned()) {
            throw new BusinessRuleException("Организатор заблокирован и не может принимать новые билеты");
        }
    }

    private Event resolveLinkedEvent(CreateTicketRequest request, Organizer organizer) {
        if (request.eventId() == null || request.eventId().isBlank()) {
            return null;
        }

        return eventRepository
                .findByOrganizerIdAndEventIdIgnoreCase(
                        organizer.getId(),
                        request.eventId().trim()
                )
                .orElseThrow(() -> new BusinessRuleException("ID мероприятия указан, но мероприятие не найдено у выбранного организатора"));
    }

    private TicketLot loadTicket(Long id) {
        return ticketRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Билет не найден"));
    }

    private User requireUser(UserDetails principal) {
        if (principal == null || principal.getUsername() == null) {
            throw new UnauthorizedException("Не авторизован");
        }

        return userRepository.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Не авторизован"));
    }

    private User tryLoadUser(UserDetails principal) {
        if (principal == null || principal.getUsername() == null) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(principal.getUsername()).orElse(null);
    }

    private void ensureSellerCanModifyListing(TicketLot ticket, User seller) {
        ensureSellerOwnsTicket(ticket, seller);

        if (ticket.getStatus() == TicketStatus.PROCESSING || ticket.getStatus() == TicketStatus.COMPLETED) {
            throw new BusinessRuleException("Объявление нельзя изменить после начала покупки");
        }

        if (hasActiveHold(ticket.getId())) {
            throw new BusinessRuleException("Объявление нельзя изменить, пока оно зарезервировано покупателем");
        }
    }

    private void ensureSellerCanModifyFile(TicketLot ticket, User seller) {
        ensureSellerOwnsTicket(ticket, seller);

        if (ticket.getStatus() == TicketStatus.PROCESSING || ticket.getStatus() == TicketStatus.COMPLETED) {
            throw new BusinessRuleException("Файлы билета нельзя изменить после начала покупки");
        }

        if (hasActiveHold(ticket.getId())) {
            throw new BusinessRuleException("Файлы билета нельзя изменить, пока объявление зарезервировано покупателем");
        }
    }

    private void ensureSellerOwnsTicket(TicketLot ticket, User seller) {
        if (ticket.getSeller() == null || ticket.getSeller().getId() == null || !ticket.getSeller().getId().equals(seller.getId())) {
            throw new UnauthorizedException("Можно изменять только свои объявления");
        }
    }

    private void ensureCanReadTicketFiles(TicketLot ticket, User currentUser) {
        boolean isSeller = isSeller(ticket, currentUser);
        boolean isCompletedBuyer = currentUser != null
                && ticket.getStatus() == TicketStatus.COMPLETED
                && isBuyer(ticket, currentUser);
        boolean isOrganizer = isOrganizerForTicket(ticket, currentUser);

        if (!isSeller && !isCompletedBuyer && !isOrganizer) {
            throw new UnauthorizedException("У вас нет доступа к этим файлам билета");
        }
    }

    private void ensureCanReadReissuedTicket(TicketLot ticket, User currentUser) {
        if (ticket.getStatus() != TicketStatus.COMPLETED || !isBuyer(ticket, currentUser)) {
            throw new UnauthorizedException("Новый билет доступен только покупателю после завершения сделки");
        }
    }

    private boolean isBuyer(TicketLot ticket, User currentUser) {
        return currentUser != null
                && ticket.getBuyer() != null
                && ticket.getBuyer().getId() != null
                && ticket.getBuyer().getId().equals(currentUser.getId());
    }

    private boolean isOrganizerForTicket(TicketLot ticket, User currentUser) {
        return currentUser != null
                && ticket.getOrganizer() != null
                && ticket.getOrganizer().getContactEmail() != null
                && ticket.getOrganizer().getContactEmail().equalsIgnoreCase(currentUser.getEmail());
    }

    private boolean isSeller(TicketLot ticket, User currentUser) {
        return currentUser != null
                && ticket.getSeller() != null
                && ticket.getSeller().getId() != null
                && ticket.getSeller().getId().equals(currentUser.getId());
    }

    private boolean isVisibleForPublic(TicketLot ticket) {
        return ticket.getStatus() == TicketStatus.PENDING_RECIPIENT;
    }

    private boolean hasActiveHold(Long listingId) {
        return listingHoldRepository.findByListingIdAndHoldUntilAfter(listingId, Instant.now()).isPresent();
    }

    private boolean requiresRevalidation(TicketLot ticket, CreateTicketRequest request, VenueParts newVenueParts) {
        if (ticket.getStatus() == TicketStatus.FAILED
                || ticket.getStatus() == TicketStatus.CREATED
                || ticket.getStatus() == TicketStatus.PENDING_VALIDATION) {
            return true;
        }

        Organizer newOrganizer = resolveOrganizer(request);

        if (organizerRepository == null) {
            return !safeEquals(ticket.getUid(), request.uid())
                    || !safeEquals(ticket.getEventName(), request.eventName())
                    || !safeEquals(ticket.getEventDate(), request.eventDate())
                    || !safeEquals(ticket.getVenueName(), newVenueParts.venueName())
                    || !safeEquals(ticket.getVenueCity(), newVenueParts.venueCity())
                    || !safeEquals(
                    partnerOrganizerCodeMapper.normalizeOrganizerName(ticket.getOrganizerName()),
                    partnerOrganizerCodeMapper.normalizeOrganizerName(request.organizerName())
            )
                    || !safeEquals(
                    ticket.getEvent() == null ? null : ticket.getEvent().getEventId(),
                    request.eventId()
            );
        }

        return !safeEquals(ticket.getUid(), request.uid())
                || !safeEquals(ticket.getEventName(), request.eventName())
                || !safeEquals(ticket.getEventDate(), request.eventDate())
                || !safeEquals(ticket.getVenueName(), newVenueParts.venueName())
                || !safeEquals(ticket.getVenueCity(), newVenueParts.venueCity())
                || !safeEquals(ticket.getOrganizer() == null ? null : ticket.getOrganizer().getId(), newOrganizer.getId())
                || !safeEquals(
                ticket.getEvent() == null ? null : ticket.getEvent().getEventId(),
                request.eventId()
        );
    }

    private void applyEditableFields(TicketLot ticket, CreateTicketRequest request, VenueParts venueParts) {
        Organizer organizer = resolveOrganizer(request);
        ticket.setUid(request.uid());
        ticket.setEventName(request.eventName());
        ticket.setEventDate(request.eventDate());
        ticket.setVenueName(venueParts.venueName());
        ticket.setVenueCity(venueParts.venueCity());
        ticket.setAdditionalInfo(request.additionalInfo());
        ticket.setOrganizer(organizer);
        ticket.setOrganizerName(organizer.getName());
        ticket.setEvent(resolveLinkedEvent(request, organizer));
        ticket.setSellerComment(request.sellerComment());
        ticket.setOriginalPrice(request.price());
        ticket.setResalePrice(request.price());
    }

    private boolean safeEquals(Object left, Object right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
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
        return toDetailsResponse(ticket, null);
    }

    private ListingDetailsResponse toDetailsResponse(TicketLot ticket, User viewer) {
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
                isBuyer(ticket, viewer) ? ticket.getReissuedTicketUid() : null,
                sellerInfo,
                ticket.hasTicketFile(),
                ticket.getTicketFilesCount()
        );
    }

    private List<MultipartFile> collectFiles(List<MultipartFile> ticketFiles, MultipartFile singleTicketFile) {
        List<MultipartFile> files = new ArrayList<>();

        if (ticketFiles != null) {
            for (MultipartFile file : ticketFiles) {
                if (file != null && !file.isEmpty()) {
                    files.add(file);
                }
            }
        }

        if (singleTicketFile != null && !singleTicketFile.isEmpty()) {
            files.add(singleTicketFile);
        }

        return files;
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
