package ru.ticketswap.admin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketswap.admin.dto.CreateOrganizerRequest;
import ru.ticketswap.common.BusinessRuleException;
import ru.ticketswap.common.ConflictException;
import ru.ticketswap.common.NotFoundException;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerRepository;
import ru.ticketswap.organizer.OrganizerVerificationMode;
import ru.ticketswap.hold.ListingHoldRepository;
import ru.ticketswap.purchase.PaymentStatus;
import ru.ticketswap.purchase.PurchaseOrder;
import ru.ticketswap.purchase.PurchaseOrderRepository;
import ru.ticketswap.purchase.PurchaseOrderStatus;
import ru.ticketswap.ticket.TicketLot;
import ru.ticketswap.ticket.TicketRepository;
import ru.ticketswap.ticket.TicketStatus;
import ru.ticketswap.ticket.history.ListingStatusHistoryService;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserIdentityService;
import ru.ticketswap.user.UserRepository;

import java.util.List;

@Service
public class AdminOrganizerService {

    private static final String ORGANIZER_ROLE = "ORGANIZER";

    private final OrganizerRepository organizerRepository;
    private final UserRepository userRepository;
    private final UserIdentityService userIdentityService;
    private final TicketRepository ticketRepository;
    private final ListingHoldRepository listingHoldRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ListingStatusHistoryService listingStatusHistoryService;

    public AdminOrganizerService(
            OrganizerRepository organizerRepository,
            UserRepository userRepository,
            UserIdentityService userIdentityService,
            TicketRepository ticketRepository,
            ListingHoldRepository listingHoldRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            ListingStatusHistoryService listingStatusHistoryService
    ) {
        this.organizerRepository = organizerRepository;
        this.userRepository = userRepository;
        this.userIdentityService = userIdentityService;
        this.ticketRepository = ticketRepository;
        this.listingHoldRepository = listingHoldRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.listingStatusHistoryService = listingStatusHistoryService;
    }

    public List<Organizer> listOrganizers() {
        return organizerRepository.findAll();
    }

    @Transactional
    public Organizer createOrganizer(CreateOrganizerRequest request) {
        String name = request.name().trim();
        String apiKey = normalizeApiKey(request.apiKey());
        String contactEmail = userIdentityService.normalizeEmail(request.contactEmail());
        OrganizerVerificationMode verificationMode = request.verificationMode() == null
                ? OrganizerVerificationMode.EXTERNAL_API
                : request.verificationMode();

        User user = userIdentityService.findUserByEmail(contactEmail)
                .orElseThrow(() -> new NotFoundException("Пользователь с такой контактной почтой не найден"));

        if (organizerRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Организатор с таким названием уже существует");
        }

        if (organizerRepository.existsByContactEmailIgnoreCase(contactEmail)) {
            throw new ConflictException("Организатор с такой контактной почтой уже существует");
        }

        if (verificationMode == OrganizerVerificationMode.EXTERNAL_API) {
            if (apiKey == null) {
                throw new BusinessRuleException("Для верифицированного организатора нужен API-ключ");
            }
            if (organizerRepository.existsByApiKeyIgnoreCase(apiKey)) {
                throw new ConflictException("Организатор с таким API-ключом уже существует");
            }
        } else {
            if (apiKey != null && organizerRepository.existsByApiKeyIgnoreCase(apiKey)) {
                throw new ConflictException("Организатор с таким API-ключом уже существует");
            }
        }

        Organizer organizer = organizerRepository.save(new Organizer(name, apiKey, contactEmail, verificationMode));
        user.setRole(ORGANIZER_ROLE);
        user.incrementTokenVersion();
        userRepository.save(user);

        return organizer;
    }

    @Transactional
    public Organizer banOrganizer(Long id) {
        Organizer organizer = loadOrganizer(id);
        organizer.setBanned(true);
        Organizer saved = organizerRepository.save(organizer);
        suspendActiveListingsForBannedOrganizer(saved);
        return saved;
    }

    @Transactional
    public Organizer unbanOrganizer(Long id) {
        Organizer organizer = loadOrganizer(id);
        organizer.setBanned(false);
        return organizerRepository.save(organizer);
    }



    private void suspendActiveListingsForBannedOrganizer(Organizer organizer) {
        for (TicketStatus status : List.of(
                TicketStatus.CREATED,
                TicketStatus.PENDING_VALIDATION,
                TicketStatus.PENDING_RECIPIENT,
                TicketStatus.PROCESSING
        )) {
            for (TicketLot listing : ticketRepository.findAllByOrganizerIdAndStatusOrderByCreatedAtAsc(organizer.getId(), status)) {
                listingHoldRepository.deleteByListingId(listing.getId());
                if (status == TicketStatus.PROCESSING) {
                    markOrdersRefundRequired(listing.getId(), "Организатор заблокирован администратором; требуется возврат платежа");
                }
                listing.setBuyer(status == TicketStatus.PROCESSING ? listing.getBuyer() : null);
                listingStatusHistoryService.transition(
                        listing,
                        TicketStatus.FAILED,
                        "Объявление остановлено: организатор заблокирован администратором",
                        null
                );
            }
        }
        listingHoldRepository.flush();
    }

    private void markOrdersRefundRequired(Long listingId, String reason) {
        for (PurchaseOrder order : purchaseOrderRepository.findAllByListingIdAndStatusIn(
                listingId,
                List.of(PurchaseOrderStatus.CREATED, PurchaseOrderStatus.PAYMENT_AUTHORIZED, PurchaseOrderStatus.PROCESSING_REISSUE, PurchaseOrderStatus.WAITING_MANUAL_REISSUE)
        )) {
            order.setStatus(PurchaseOrderStatus.REFUND_REQUIRED);
            order.setPaymentStatus(PaymentStatus.REFUND_REQUIRED);
            order.setFailureReason(reason);
            purchaseOrderRepository.save(order);
        }
        purchaseOrderRepository.flush();
    }


    private Organizer loadOrganizer(Long id) {
        return organizerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Организатор не найден"));
    }

    private String normalizeApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return apiKey.trim();
    }
}
