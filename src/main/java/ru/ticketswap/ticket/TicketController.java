package ru.ticketswap.ticket;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.ticketswap.common.BusinessRuleException;
import ru.ticketswap.common.ForbiddenException;
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
import ru.ticketswap.purchase.PurchaseOrderRepository;
import ru.ticketswap.purchase.PurchaseOrderStatus;
import ru.ticketswap.storage.TicketFileStorageService;
import ru.ticketswap.ticket.history.ListingStatusHistoryService;
import ru.ticketswap.ticket.dto.CreateTicketRequest;
import ru.ticketswap.ticket.dto.ListingDetailsResponse;
import ru.ticketswap.ticket.dto.ListingStatusHistoryResponse;
import ru.ticketswap.ticket.dto.ListingViewResponse;
import ru.ticketswap.ticket.dto.TicketFileDownloadUrlResponse;
import ru.ticketswap.ticket.dto.TicketFilePreviewsResponse;
import ru.ticketswap.ticket.dto.TicketLotPageResponse;
import ru.ticketswap.ticket.dto.TicketFilesResponse;
import ru.ticketswap.ticket.dto.TicketLotResponse;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final ListingHoldRepository listingHoldRepository;
    private final PurchaseService purchaseService;
    private final PurchaseOrderRepository purchaseOrderRepository;
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
            PurchaseOrderRepository purchaseOrderRepository,
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
        this.purchaseOrderRepository = purchaseOrderRepository;
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
                null,
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
    public ResponseEntity<?> listTickets(
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "organizerId", required = false) Long organizerId,
            @RequestParam(value = "eventDbId", required = false) Long eventDbId,
            @RequestParam(value = "selectedEventId", required = false) Long selectedEventId,
            @RequestParam(value = "eventId", required = false) String eventId,
            @RequestParam(value = "city", required = false) String city,
            @RequestParam(value = "venueCity", required = false) String venueCity,
            @RequestParam(value = "venue", required = false) String venue,
            @RequestParam(value = "venueName", required = false) String venueName,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "priceMin", required = false) BigDecimal priceMin,
            @RequestParam(value = "priceMax", required = false) BigDecimal priceMax,
            @RequestParam(value = "sort", defaultValue = "createdDesc") String sort,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "paged", required = false) Boolean paged
    ) {
        PublicListingSearch search = new PublicListingSearch(
                firstNonBlank(query, q),
                organizerId,
                eventDbId != null ? eventDbId : selectedEventId,
                eventId,
                firstNonBlank(city, venueCity),
                firstNonBlank(venue, venueName),
                parseDateBoundary(dateFrom, false, "dateFrom"),
                parseDateBoundary(dateTo, true, "dateTo"),
                priceMin,
                priceMax,
                resolveListingSort(sort),
                normalizeLimit(limit),
                normalizePage(page),
                normalizePageSize(size, limit),
                shouldReturnPagedResponse(page, size, paged)
        );
        validatePublicListingSearch(search);

        String normalizedQuery = normalizeSearchText(search.query());
        String normalizedEventId = normalizeExactText(search.eventId());
        String normalizedCity = normalizeSearchText(search.city());
        String normalizedVenue = normalizeSearchText(search.venue());

        Pageable pageable = PageRequest.of(search.page(), search.size(), search.sort());
        Page<TicketLot> pageResult = ticketRepository.searchPublicListings(
                TicketStatus.PENDING_RECIPIENT,
                LocalDateTime.now(),
                Instant.now(),
                toSqlTextParam(normalizedQuery),
                normalizedQuery == null,
                search.organizerId(),
                search.eventDbId(),
                toSqlTextParam(normalizedEventId),
                normalizedEventId == null,
                toSqlTextParam(normalizedCity),
                normalizedCity == null,
                toSqlTextParam(normalizedVenue),
                normalizedVenue == null,
                search.dateFrom(),
                search.dateTo(),
                search.priceMin(),
                search.priceMax(),
                pageable
        );

        List<TicketLotResponse> content = pageResult.getContent().stream()
                .map(this::toTicketLotResponse)
                .toList();

        if (search.paged()) {
            return ResponseEntity.ok(TicketLotPageResponse.from(pageResult, content, sort));
        }

        return ResponseEntity.ok(content);
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
        ListingViewResponse.Hold hold = canSeeHoldInfo(ticket, currentUser)
                ? activeHold.map(h -> new ListingViewResponse.Hold(h.getId(), h.getHoldUntil())).orElse(null)
                : null;

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
        User currentUser = requireUser(userDetails);
        if (!isAdmin(currentUser)) {
            throw new ForbiddenException("История статусов доступна только администратору");
        }

        loadTicket(id);

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
        throw new BusinessRuleException("Продажа без файла запрещена: используйте multipart-запрос с ticketFile или ticketFiles");
    }

    @PostMapping(value = "/sell", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ListingDetailsResponse> sellTicketWithFiles(
            @Valid @RequestPart("ticket") CreateTicketRequest request,
            @RequestPart(value = "ticketFiles", required = false) List<MultipartFile> ticketFiles,
            @RequestPart(value = "ticketFile", required = false) MultipartFile singleTicketFile,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User seller = requireUser(userDetails);
        List<MultipartFile> files = collectFiles(ticketFiles, singleTicketFile);
        if (files.isEmpty()) {
            throw new BusinessRuleException("Требуется хотя бы один файл билета");
        }
        TicketLot saved = createListing(request, seller);

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

        ResolvedListingInput resolved = resolveListingInput(request);
        boolean requiresRevalidation = requiresRevalidation(ticket, request, resolved.venueParts());

        if (requiresRevalidation && !ticket.hasTicketFile()) {
            throw new BusinessRuleException("Нельзя отправить объявление на проверку без файла билета");
        }

        applyEditableFields(ticket, request, resolved);

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

    @GetMapping("/{id}/files/previews")
    public ResponseEntity<TicketFilePreviewsResponse> listTicketFilePreviews(
            @PathVariable("id") Long id
    ) {
        TicketLot ticket = loadTicket(id);
        if (!isVisibleForPublic(ticket)) {
            throw new NotFoundException("Билет не найден");
        }
        return ResponseEntity.ok(ticketFileStorageService.listFilePreviews(ticket));
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
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        User buyer = requireUser(userDetails);
        TicketLot saved = purchaseService.buyNow(id, buyer, idempotencyKey);
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
        ResolvedListingInput resolved = resolveListingInput(request);

        TicketLot ticket = new TicketLot(
                request.uid(),
                resolved.eventName(),
                resolved.eventDate(),
                resolved.venueParts().venueName(),
                resolved.venueParts().venueCity(),
                request.price(),
                request.additionalInfo(),
                resolved.organizer().getName(),
                request.sellerComment(),
                seller
        );
        ticket.setOrganizer(resolved.organizer());
        ticket.setEvent(resolved.event());

        return listingStatusHistoryService.createListingWithInitialStatus(ticket, "Объявление создано", seller);
    }

    private ResolvedListingInput resolveListingInput(CreateTicketRequest request) {
        if (request.selectedEventId() != null) {
            Event event = eventRepository.findById(request.selectedEventId())
                    .orElseThrow(() -> new BusinessRuleException("Выбранное мероприятие не найдено"));
            Organizer organizer = event.getOrganizer();
            ensureOrganizerCanAcceptListings(organizer);
            ensureEventIsUpcoming(event);
            VenueParts venueParts = new VenueParts(
                    event.getVenue().getName(),
                    event.getVenue().getAddress()
            );
            return new ResolvedListingInput(
                    event.getName(),
                    event.getStartsAt().atZone(ZoneId.of(event.getVenue().getTimezone())).toLocalDateTime(),
                    venueParts,
                    organizer,
                    event
            );
        }

        Organizer organizer = resolveOrganizer(request);
        Event linkedEvent = resolveLinkedEvent(request, organizer);
        if (organizer.isExternalApi() && linkedEvent == null) {
            throw new BusinessRuleException("Для внешнего организатора нужно выбрать мероприятие из каталога или передать eventId");
        }
        if (linkedEvent != null) {
            VenueParts venueParts = new VenueParts(linkedEvent.getVenue().getName(), linkedEvent.getVenue().getAddress());
            return new ResolvedListingInput(
                    linkedEvent.getName(),
                    linkedEvent.getStartsAt().atZone(ZoneId.of(linkedEvent.getVenue().getTimezone())).toLocalDateTime(),
                    venueParts,
                    organizer,
                    linkedEvent
            );
        }

        validateManualEventFields(request);
        VenueParts venueParts = parseVenueParts(request.venue());
        return new ResolvedListingInput(
                request.eventName().trim(),
                request.eventDate(),
                venueParts,
                organizer,
                null
        );
    }

    private void validateManualEventFields(CreateTicketRequest request) {
        if (request.eventName() == null || request.eventName().isBlank()) {
            throw new BusinessRuleException("Название мероприятия обязательно, если мероприятие не выбрано из поиска");
        }
        if (request.eventDate() == null) {
            throw new BusinessRuleException("Дата мероприятия обязательна, если мероприятие не выбрано из поиска");
        }
        if (!request.eventDate().isAfter(LocalDateTime.now())) {
            throw new BusinessRuleException("Дата мероприятия должна быть в будущем");
        }
        if (request.venue() == null || request.venue().isBlank()) {
            throw new BusinessRuleException("Площадка обязательна, если мероприятие не выбрано из поиска");
        }
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
        Organizer organizer = organizerRepository.findByOrganizerCodeIgnoreCase(normalized)
                .or(() -> organizerRepository.findByNameIgnoreCase(normalized))
                .orElseThrow(() -> new BusinessRuleException("Организатор не зарегистрирован"));
        ensureOrganizerCanAcceptListings(organizer);
        return organizer;
    }

    private void ensureOrganizerCanAcceptListings(Organizer organizer) {
        if (organizer == null) {
            throw new BusinessRuleException("Нужно выбрать зарегистрированного организатора");
        }
        if (organizer.isBanned()) {
            throw new BusinessRuleException("Организатор заблокирован и не может принимать новые билеты");
        }
    }

    private void ensureEventIsUpcoming(Event event) {
        if (event.getStartsAt() == null || !event.getStartsAt().isAfter(Instant.now())) {
            throw new BusinessRuleException("Выбранное мероприятие уже прошло");
        }
    }

    private Event resolveLinkedEvent(CreateTicketRequest request, Organizer organizer) {
        if (request.eventId() == null || request.eventId().isBlank()) {
            return null;
        }

        Event event = eventRepository
                .findByOrganizerIdAndEventIdIgnoreCase(
                        organizer.getId(),
                        request.eventId().trim()
                )
                .orElseThrow(() -> new BusinessRuleException("ID мероприятия указан, но мероприятие не найдено у выбранного организатора"));
        ensureEventIsUpcoming(event);
        return event;
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

        if (hasRefundRequiredOrder(ticket.getId())) {
            throw new BusinessRuleException("Объявление нельзя повторно выставить, пока по предыдущей покупке требуется возврат");
        }
    }

    private void ensureSellerCanModifyFile(TicketLot ticket, User seller) {
        ensureSellerOwnsTicket(ticket, seller);

        if (ticket.getStatus() == TicketStatus.PENDING_VALIDATION
                || ticket.getStatus() == TicketStatus.PENDING_RECIPIENT
                || ticket.getStatus() == TicketStatus.PROCESSING
                || ticket.getStatus() == TicketStatus.COMPLETED) {
            throw new BusinessRuleException("Файлы билета нельзя изменить после отправки объявления на проверку; создайте новое объявление или измените данные объявления для повторной проверки");
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
        boolean isSellerBeforePurchase = isSeller(ticket, currentUser)
                && ticket.getBuyer() == null
                && ticket.getStatus() != TicketStatus.PROCESSING
                && ticket.getStatus() != TicketStatus.COMPLETED;
        boolean isCompletedBuyer = currentUser != null
                && ticket.getStatus() == TicketStatus.COMPLETED
                && isBuyer(ticket, currentUser);
        boolean isOrganizer = isOrganizerForTicket(ticket, currentUser);

        if (!isSellerBeforePurchase && !isCompletedBuyer && !isOrganizer) {
            throw new UnauthorizedException("У вас нет доступа к этим файлам билета");
        }
    }

    private void ensureCanReadReissuedTicket(TicketLot ticket, User currentUser) {
        if (ticket.getStatus() != TicketStatus.COMPLETED || !isBuyer(ticket, currentUser)) {
            throw new UnauthorizedException("Новый билет доступен только покупателю после завершения сделки");
        }
    }

    private boolean isAdmin(User currentUser) {
        return currentUser != null && "ADMIN".equalsIgnoreCase(currentUser.getRole());
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
        return ticket.getStatus() == TicketStatus.PENDING_RECIPIENT
                && ticket.getOrganizer() != null
                && !ticket.getOrganizer().isBanned()
                && ticket.getEventDate() != null
                && ticket.getEventDate().isAfter(LocalDateTime.now());
    }

    private boolean canSeeHoldInfo(TicketLot ticket, User currentUser) {
        return isAdmin(currentUser) || isSeller(ticket, currentUser) || isBuyer(ticket, currentUser) || isOrganizerForTicket(ticket, currentUser);
    }

    private boolean canSeePrivateListingFields(TicketLot ticket, User viewer) {
        return isAdmin(viewer) || isSeller(ticket, viewer) || isBuyer(ticket, viewer) || isOrganizerForTicket(ticket, viewer);
    }

    private boolean hasRefundRequiredOrder(Long listingId) {
        return purchaseOrderRepository != null
                && !purchaseOrderRepository.findAllByListingIdAndStatusIn(listingId, List.of(PurchaseOrderStatus.REFUND_REQUIRED)).isEmpty();
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

    private boolean requiresRevalidation(TicketLot ticket, CreateTicketRequest request, ResolvedListingInput resolved) {
        if (ticket.getStatus() == TicketStatus.FAILED
                || ticket.getStatus() == TicketStatus.CREATED
                || ticket.getStatus() == TicketStatus.PENDING_VALIDATION) {
            return true;
        }

        if (organizerRepository == null) {
            return !safeEquals(ticket.getUid(), request.uid())
                    || !safeEquals(ticket.getEventName(), resolved.eventName())
                    || !safeEquals(ticket.getEventDate(), resolved.eventDate())
                    || !safeEquals(ticket.getVenueName(), resolved.venueParts().venueName())
                    || !safeEquals(ticket.getVenueCity(), resolved.venueParts().venueCity())
                    || !safeEquals(
                    ticket.getEvent() == null ? null : ticket.getEvent().getEventId(),
                    resolved.event() == null ? null : resolved.event().getEventId()
            );
        }

        return !safeEquals(ticket.getUid(), request.uid())
                || !safeEquals(ticket.getEventName(), resolved.eventName())
                || !safeEquals(ticket.getEventDate(), resolved.eventDate())
                || !safeEquals(ticket.getVenueName(), resolved.venueParts().venueName())
                || !safeEquals(ticket.getVenueCity(), resolved.venueParts().venueCity())
                || !safeEquals(ticket.getOrganizer() == null ? null : ticket.getOrganizer().getId(), resolved.organizer().getId())
                || !safeEquals(
                ticket.getEvent() == null ? null : ticket.getEvent().getId(),
                resolved.event() == null ? null : resolved.event().getId()
        );
    }

    private void applyEditableFields(TicketLot ticket, CreateTicketRequest request, ResolvedListingInput resolved) {
        ticket.setUid(request.uid());
        ticket.setEventName(resolved.eventName());
        ticket.setEventDate(resolved.eventDate());
        ticket.setVenueName(resolved.venueParts().venueName());
        ticket.setVenueCity(resolved.venueParts().venueCity());
        ticket.setAdditionalInfo(request.additionalInfo());
        ticket.setOrganizer(resolved.organizer());
        ticket.setOrganizerName(resolved.organizer().getName());
        ticket.setEvent(resolved.event());
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
                displayName = "seller-" + ticket.getSeller().getId();
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
                canSeePrivateListingFields(ticket, viewer) ? ticket.getSellerComment() : null,
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

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private String normalizeSearchText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private String normalizeExactText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    private String toSqlTextParam(String value) {
        return value == null ? "" : value;
    }

    private Integer normalizeLimit(Integer limit) {
        if (limit == null) {
            return 100;
        }
        if (limit < 1) {
            throw new BusinessRuleException("limit must be positive");
        }
        return Math.min(limit, 100);
    }

    private Integer normalizePage(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw new BusinessRuleException("page cannot be negative");
        }
        return page;
    }

    private Integer normalizePageSize(Integer size, Integer limit) {
        Integer effectiveSize = size != null ? size : normalizeLimit(limit);
        if (effectiveSize < 1) {
            throw new BusinessRuleException("size must be positive");
        }
        return Math.min(effectiveSize, 100);
    }

    private boolean shouldReturnPagedResponse(Integer page, Integer size, Boolean paged) {
        return Boolean.TRUE.equals(paged) || page != null || size != null;
    }

    private LocalDateTime parseDateBoundary(String value, boolean endOfDay, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException ignored) {
            // Try other accepted public-filter formats below.
        }

        try {
            return OffsetDateTime.parse(trimmed).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // Try date-only format below.
        }

        try {
            LocalDate date = LocalDate.parse(trimmed);
            if (endOfDay) {
                return LocalDateTime.of(date, LocalTime.MAX);
            }
            return date.atStartOfDay();
        } catch (DateTimeParseException ex) {
            throw new BusinessRuleException(fieldName + " должен быть в формате ISO: yyyy-MM-dd или yyyy-MM-ddTHH:mm:ss");
        }
    }

    private Sort resolveListingSort(String sort) {
        String normalized = sort == null || sort.isBlank() ? "createdDesc" : sort.trim();
        return switch (normalized) {
            case "createdDesc" -> Sort.by(Sort.Direction.DESC, "createdAt");
            case "createdAsc" -> Sort.by(Sort.Direction.ASC, "createdAt");
            case "eventDateAsc" -> Sort.by(Sort.Direction.ASC, "eventDate").and(Sort.by(Sort.Direction.ASC, "createdAt"));
            case "eventDateDesc" -> Sort.by(Sort.Direction.DESC, "eventDate").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "priceAsc" -> Sort.by(Sort.Direction.ASC, "resalePrice").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            case "priceDesc" -> Sort.by(Sort.Direction.DESC, "resalePrice").and(Sort.by(Sort.Direction.DESC, "createdAt"));
            default -> throw new BusinessRuleException("Неподдерживаемая сортировка: " + normalized);
        };
    }

    private void validatePublicListingSearch(PublicListingSearch search) {
        if (search.priceMin() != null && search.priceMin().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("priceMin не может быть отрицательным");
        }
        if (search.priceMax() != null && search.priceMax().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException("priceMax не может быть отрицательным");
        }
        if (search.priceMin() != null && search.priceMax() != null && search.priceMin().compareTo(search.priceMax()) > 0) {
            throw new BusinessRuleException("priceMin не может быть больше priceMax");
        }
        if (search.dateFrom() != null && search.dateTo() != null && search.dateFrom().isAfter(search.dateTo())) {
            throw new BusinessRuleException("dateFrom не может быть позже dateTo");
        }
    }

    private record PublicListingSearch(
            String query,
            Long organizerId,
            Long eventDbId,
            String eventId,
            String city,
            String venue,
            LocalDateTime dateFrom,
            LocalDateTime dateTo,
            BigDecimal priceMin,
            BigDecimal priceMax,
            Sort sort,
            Integer limit,
            Integer page,
            Integer size,
            boolean paged
    ) {
    }

    private record ResolvedListingInput(
            String eventName,
            LocalDateTime eventDate,
            VenueParts venueParts,
            Organizer organizer,
            Event event
    ) {
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
