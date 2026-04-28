package ru.ticketswap.admin;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketswap.admin.dto.CreateOrganizerRequest;
import ru.ticketswap.audit.AuditLogService;
import ru.ticketswap.common.BusinessRuleException;
import ru.ticketswap.common.ConflictException;
import ru.ticketswap.common.NotFoundException;
import ru.ticketswap.hold.ListingHoldRepository;
import ru.ticketswap.notification.NotificationOutboxService;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerRepository;
import ru.ticketswap.organizer.OrganizerVerificationMode;
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

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
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
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final NotificationOutboxService notificationOutboxService;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminOrganizerService(
            OrganizerRepository organizerRepository,
            UserRepository userRepository,
            UserIdentityService userIdentityService,
            TicketRepository ticketRepository,
            ListingHoldRepository listingHoldRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            ListingStatusHistoryService listingStatusHistoryService,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService,
            NotificationOutboxService notificationOutboxService
    ) {
        this.organizerRepository = organizerRepository;
        this.userRepository = userRepository;
        this.userIdentityService = userIdentityService;
        this.ticketRepository = ticketRepository;
        this.listingHoldRepository = listingHoldRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.listingStatusHistoryService = listingStatusHistoryService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.notificationOutboxService = notificationOutboxService;
    }

    public List<Organizer> listOrganizers() {
        return organizerRepository.findAll();
    }

    @Transactional
    public OrganizerCreationResult createOrganizer(CreateOrganizerRequest request, User actor) {
        String name = requiredTrim(request.name(), "Название обязательно");
        String organizerCode = normalizeOrganizerCode(request.organizerCode());
        if (organizerCode == null) {
            organizerCode = normalizeOrganizerCode(request.apiKey());
        }
        String contactEmail = userIdentityService.normalizeEmail(request.contactEmail());
        OrganizerVerificationMode verificationMode = request.verificationMode() == null
                ? OrganizerVerificationMode.EXTERNAL_API
                : request.verificationMode();

        if (organizerCode == null) {
            throw new BusinessRuleException("Код организатора обязателен");
        }

        User user = userIdentityService.findUserByEmail(contactEmail)
                .orElseThrow(() -> new NotFoundException("Пользователь с такой контактной почтой не найден"));

        if (organizerRepository.existsByNameIgnoreCase(name)) {
            throw new ConflictException("Организатор с таким названием уже существует");
        }

        if (organizerRepository.existsByContactEmailIgnoreCase(contactEmail)) {
            throw new ConflictException("Организатор с такой контактной почтой уже существует");
        }

        if (organizerRepository.existsByOrganizerCodeIgnoreCase(organizerCode)) {
            throw new ConflictException("Организатор с таким кодом уже существует");
        }

        String generatedSecret = null;
        Organizer organizer = new Organizer(name, organizerCode, contactEmail, verificationMode);
        if (verificationMode == OrganizerVerificationMode.EXTERNAL_API) {
            String secret = request.integrationSecret();
            if (secret == null || secret.isBlank()) {
                secret = generateSecret();
                generatedSecret = secret;
            }
            organizer.setApiKeyHash(passwordEncoder.encode(secret));
            organizer.setApiKeyLast4(last4(secret));
            organizer.setApiKeyCreatedAt(Instant.now());
        }

        Organizer saved = organizerRepository.save(organizer);
        user.setRole(ORGANIZER_ROLE);
        user.incrementTokenVersion();
        userRepository.save(user);

        auditLogService.record(actor, "ORGANIZER_CREATED", "ORGANIZER", saved.getId(), "code=" + saved.getOrganizerCode());
        notificationOutboxService.enqueue(contactEmail, "ORGANIZER_CREATED", "Профиль организатора создан", "Организатор: " + name);

        return new OrganizerCreationResult(saved, generatedSecret);
    }

    @Transactional
    public Organizer banOrganizer(Long id, User actor) {
        Organizer organizer = loadOrganizer(id);
        organizer.setBanned(true);
        Organizer saved = organizerRepository.save(organizer);
        suspendActiveListingsForBannedOrganizer(saved);
        auditLogService.record(actor, "ORGANIZER_BANNED", "ORGANIZER", saved.getId(), saved.getOrganizerCode());
        notificationOutboxService.enqueue(saved.getContactEmail(), "ORGANIZER_BANNED", "Организатор заблокирован", "Организатор: " + saved.getName());
        return saved;
    }

    @Transactional
    public Organizer unbanOrganizer(Long id, User actor) {
        Organizer organizer = loadOrganizer(id);
        organizer.setBanned(false);
        Organizer saved = organizerRepository.save(organizer);
        auditLogService.record(actor, "ORGANIZER_UNBANNED", "ORGANIZER", saved.getId(), saved.getOrganizerCode());
        notificationOutboxService.enqueue(saved.getContactEmail(), "ORGANIZER_UNBANNED", "Организатор разблокирован", "Организатор: " + saved.getName());
        return saved;
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
            notificationOutboxService.enqueue(order.getBuyer().getEmail(), "REFUND_REQUIRED", "Требуется возврат платежа", reason);
        }
        purchaseOrderRepository.flush();
    }

    private Organizer loadOrganizer(Long id) {
        return organizerRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Организатор не найден"));
    }

    private String normalizeOrganizerCode(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String requiredTrim(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException(message);
        }
        return value.trim();
    }

    private String generateSecret() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String last4(String value) {
        if (value == null || value.length() <= 4) {
            return value;
        }
        return value.substring(value.length() - 4);
    }

    public record OrganizerCreationResult(Organizer organizer, String generatedIntegrationSecret) {
    }
}
