package ru.ticketswap.organizer.manual;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.ticketswap.common.BusinessRuleException;
import ru.ticketswap.audit.AuditLogService;
import ru.ticketswap.common.NotFoundException;
import ru.ticketswap.notification.NotificationOutboxService;
import ru.ticketswap.payment.PaymentCaptureResponse;
import ru.ticketswap.payment.PaymentGatewayClient;
import ru.ticketswap.payment.PaymentIntegrationException;
import ru.ticketswap.hold.ListingHoldRepository;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerVerificationMode;
import ru.ticketswap.purchase.PaymentStatus;
import ru.ticketswap.purchase.PurchaseOrder;
import ru.ticketswap.purchase.PurchaseOrderRepository;
import ru.ticketswap.purchase.PurchaseOrderStatus;
import ru.ticketswap.storage.TicketFileStorageService;
import ru.ticketswap.ticket.TicketLot;
import ru.ticketswap.ticket.TicketRepository;
import ru.ticketswap.ticket.TicketStatus;
import ru.ticketswap.ticket.history.ListingStatusHistoryService;
import ru.ticketswap.user.User;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class ManualOrganizerWorkflowService {

    private static final String MANUAL_VALIDATION_APPROVED_REASON = "Организатор вручную подтвердил подлинность билета";
    private static final String MANUAL_VALIDATION_REJECTED_REASON = "Организатор вручную отклонил билет";
    private static final String MANUAL_REISSUE_COMPLETED_REASON = "Организатор вручную аннулировал старый билет и загрузил новый билет для покупателя";
    private static final String MANUAL_REISSUE_REJECTED_REASON = "Организатор не смог вручную перевыпустить билет";
    private static final Pattern SAFE_TICKET_UID = Pattern.compile("^[A-Za-z0-9_.:-]{4,128}$");

    private final TicketRepository ticketRepository;
    private final ListingStatusHistoryService listingStatusHistoryService;
    private final ListingHoldRepository listingHoldRepository;
    private final TicketFileStorageService ticketFileStorageService;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final AuditLogService auditLogService;
    private final NotificationOutboxService notificationOutboxService;

    public ManualOrganizerWorkflowService(
            TicketRepository ticketRepository,
            ListingStatusHistoryService listingStatusHistoryService,
            ListingHoldRepository listingHoldRepository,
            TicketFileStorageService ticketFileStorageService,
            PurchaseOrderRepository purchaseOrderRepository,
            PaymentGatewayClient paymentGatewayClient,
            AuditLogService auditLogService,
            NotificationOutboxService notificationOutboxService
    ) {
        this.ticketRepository = ticketRepository;
        this.listingStatusHistoryService = listingStatusHistoryService;
        this.listingHoldRepository = listingHoldRepository;
        this.ticketFileStorageService = ticketFileStorageService;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.paymentGatewayClient = paymentGatewayClient;
        this.auditLogService = auditLogService;
        this.notificationOutboxService = notificationOutboxService;
    }

    public List<TicketLot> listPendingValidation(Organizer organizer) {
        ensureManualOrganizer(organizer);
        return ticketRepository.findAllByOrganizerIdAndStatusOrderByCreatedAtAsc(
                organizer.getId(),
                TicketStatus.PENDING_VALIDATION
        );
    }

    public List<TicketLot> listPendingReissue(Organizer organizer) {
        ensureManualOrganizer(organizer);
        return ticketRepository.findAllByOrganizerIdAndStatusAndBuyerIsNotNullOrderByCreatedAtAsc(
                organizer.getId(),
                TicketStatus.PROCESSING
        );
    }

    @Transactional
    public TicketLot verifyTicket(Organizer organizer, Long listingId, boolean approved, String reason, User actor) {
        ensureManualOrganizer(organizer);
        TicketLot listing = loadOrganizerListing(organizer, listingId);

        if (listing.getStatus() != TicketStatus.PENDING_VALIDATION) {
            throw new BusinessRuleException("Билет не ожидает ручной проверки");
        }

        if (approved) {
            ensureListingHasFile(listing);
            return listingStatusHistoryService.transition(
                    listing,
                    TicketStatus.PENDING_RECIPIENT,
                    buildReason(MANUAL_VALIDATION_APPROVED_REASON, reason),
                    actor
            );
        }

        return listingStatusHistoryService.transition(
                listing,
                TicketStatus.FAILED,
                buildReason(MANUAL_VALIDATION_REJECTED_REASON, reason),
                actor
        );
    }

    @Transactional
    public TicketLot completeManualReissue(
            Organizer organizer,
            Long listingId,
            String newTicketUid,
            MultipartFile ticketFile,
            User actor
    ) {
        ensureManualOrganizer(organizer);
        TicketLot listing = loadOrganizerListing(organizer, listingId);

        if (listing.getStatus() != TicketStatus.PROCESSING || listing.getBuyer() == null) {
            throw new BusinessRuleException("Билет не ожидает ручного перевыпуска");
        }

        String normalizedNewTicketUid = normalizeNewTicketUid(listing, newTicketUid);
        listing.setReissuedTicketUid(normalizedNewTicketUid);
        ticketFileStorageService.uploadReissuedTicketFile(listing, ticketFile);
        TicketLot saved = listingStatusHistoryService.transition(
                listing,
                TicketStatus.COMPLETED,
                MANUAL_REISSUE_COMPLETED_REASON,
                actor
        );
        markActiveOrdersCompleted(listingId);
        listingHoldRepository.deleteByListingId(listingId);
        return saved;
    }

    @Transactional
    public TicketLot rejectManualReissue(Organizer organizer, Long listingId, String reason, User actor) {
        ensureManualOrganizer(organizer);
        TicketLot listing = loadOrganizerListing(organizer, listingId);

        if (listing.getStatus() != TicketStatus.PROCESSING || listing.getBuyer() == null) {
            throw new BusinessRuleException("Билет не ожидает ручного перевыпуска");
        }

        String failureReason = buildReason(MANUAL_REISSUE_REJECTED_REASON, reason);
        TicketLot saved = listingStatusHistoryService.transition(
                listing,
                TicketStatus.FAILED,
                failureReason,
                actor
        );
        markActiveOrdersRefundRequired(listingId, failureReason);
        listingHoldRepository.deleteByListingId(listingId);
        return saved;
    }



    private void markActiveOrdersCompleted(Long listingId) {
        for (PurchaseOrder order : purchaseOrderRepository.findAllByListingIdAndStatusIn(
                listingId,
                List.of(PurchaseOrderStatus.CREATED, PurchaseOrderStatus.PAYMENT_AUTHORIZED, PurchaseOrderStatus.PROCESSING_REISSUE, PurchaseOrderStatus.WAITING_MANUAL_REISSUE)
        )) {
            PaymentCaptureResponse capture = capturePayment(order);
            if (!capture.captured()) {
                order.setStatus(PurchaseOrderStatus.REFUND_REQUIRED);
                order.setPaymentStatus(PaymentStatus.REFUND_REQUIRED);
                order.setFailureReason("Платёж не захвачен после ручного перевыпуска: " + failureReason(capture.reason()));
            } else {
                order.setStatus(PurchaseOrderStatus.COMPLETED);
                order.setPaymentStatus(PaymentStatus.CAPTURED);
                order.setCompletedAt(java.time.Instant.now());
            }
            purchaseOrderRepository.save(order);
        }
        purchaseOrderRepository.flush();
    }

    private PaymentCaptureResponse capturePayment(PurchaseOrder order) {
        try {
            return paymentGatewayClient.capture(order.getPaymentOperationId());
        } catch (PaymentIntegrationException ex) {
            return new PaymentCaptureResponse(false, order.getPaymentOperationId(), "платёжный сервис недоступен");
        }
    }

    private String failureReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "причина неизвестна";
        }
        return reason.trim();
    }

    private void markActiveOrdersRefundRequired(Long listingId, String failureReason) {
        for (PurchaseOrder order : purchaseOrderRepository.findAllByListingIdAndStatusIn(
                listingId,
                List.of(PurchaseOrderStatus.CREATED, PurchaseOrderStatus.PAYMENT_AUTHORIZED, PurchaseOrderStatus.PROCESSING_REISSUE, PurchaseOrderStatus.WAITING_MANUAL_REISSUE)
        )) {
            order.setStatus(PurchaseOrderStatus.REFUND_REQUIRED);
            order.setPaymentStatus(PaymentStatus.REFUND_REQUIRED);
            order.setFailureReason(failureReason);
            purchaseOrderRepository.save(order);
        }
        purchaseOrderRepository.flush();
    }


    private TicketLot loadOrganizerListing(Organizer organizer, Long listingId) {
        TicketLot listing = ticketRepository.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Билет не найден"));
        if (listing.getOrganizer() == null || !organizer.getId().equals(listing.getOrganizer().getId())) {
            throw new NotFoundException("Билет не найден у этого организатора");
        }
        return listing;
    }

    private void ensureManualOrganizer(Organizer organizer) {
        if (organizer.getVerificationMode() != OrganizerVerificationMode.MANUAL) {
            throw new BusinessRuleException("Эта операция доступна только организаторам с ручной проверкой");
        }
        if (organizer.isBanned()) {
            throw new BusinessRuleException("Организатор заблокирован");
        }
    }

    private void ensureListingHasFile(TicketLot listing) {
        if (listing.getTicketFiles() == null || listing.getTicketFiles().isEmpty()) {
            throw new BusinessRuleException("Нельзя подтвердить объявление без файла билета");
        }
    }

    private String normalizeNewTicketUid(TicketLot listing, String newTicketUid) {
        if (newTicketUid == null || newTicketUid.isBlank()) {
            throw new BusinessRuleException("UID нового билета обязателен");
        }
        String normalized = newTicketUid.trim();
        if (!SAFE_TICKET_UID.matcher(normalized).matches()) {
            throw new BusinessRuleException("UID нового билета должен быть от 4 до 128 символов и содержать только буквы, цифры, точку, подчёркивание, дефис или двоеточие");
        }
        if (normalized.equalsIgnoreCase(listing.getUid())) {
            throw new BusinessRuleException("UID нового билета не должен совпадать со старым UID");
        }
        return normalized;
    }

    private String buildReason(String base, String comment) {
        if (comment == null || comment.isBlank()) {
            return base;
        }
        return base + ": " + comment.trim();
    }
}
