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
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.organizer.OrganizerVerificationMode;
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
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class PurchaseService {

    private static final long DEFAULT_HOLD_SECONDS = 5 * 60;
    private static final String MANUAL_REISSUE_WAITING_REASON = "Оплата условно авторизована; ожидает ручного аннулирования старого билета и загрузки нового билета организатором";
    private static final Pattern SAFE_IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9_.:-]{8,128}$");

    private final TicketRepository ticketRepository;
    private final ListingHoldRepository listingHoldRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final TransactionTemplate tx;
    private final ListingStatusHistoryService listingStatusHistoryService;
    private final PartnerApiClient partnerApiClient;
    private final PartnerOrganizerCodeMapper partnerOrganizerCodeMapper;

    public PurchaseService(
            TicketRepository ticketRepository,
            ListingHoldRepository listingHoldRepository,
            PurchaseOrderRepository purchaseOrderRepository,
            PlatformTransactionManager transactionManager,
            ListingStatusHistoryService listingStatusHistoryService,
            PartnerApiClient partnerApiClient,
            PartnerOrganizerCodeMapper partnerOrganizerCodeMapper
    ) {
        this.ticketRepository = ticketRepository;
        this.listingHoldRepository = listingHoldRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
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
        return buyNow(listingId, buyer, null);
    }

    public TicketLot buyNow(Long listingId, User buyer, String idempotencyKey) {
        String normalizedIdempotencyKey = normalizeIdempotencyKey(idempotencyKey);

        TicketLot alreadyCompleted = tx.execute(status -> loadCompletedPurchaseIfAlreadyBought(listingId, buyer));
        if (alreadyCompleted != null) {
            return alreadyCompleted;
        }

        StartProcessingResult started = tx.execute(status -> startProcessingTx(listingId, buyer, normalizedIdempotencyKey));
        if (started == null) {
            throw new ConflictException("Покупка не была начата");
        }
        if (started.completedListing() != null) {
            return started.completedListing();
        }

        if (started.manual()) {
            return tx.execute(status -> markWaitingManualReissueTx(listingId, buyer, started.orderId()));
        }

        tx.executeWithoutResult(status -> markPartnerOperationStartedTx(started.orderId()));
        ReissueResult reissueResult = reissueTicketWithPartner(listingId, buyer);
        if (!reissueResult.success()) {
            return tx.execute(status -> failPurchaseTx(listingId, buyer, started.orderId(), reissueResult.failureReason()));
        }

        return tx.execute(status -> completePurchaseTx(listingId, buyer, started.orderId(), reissueResult.reissuedTicketUid()));
    }

    private TicketLot loadCompletedPurchaseIfAlreadyBought(Long listingId, User buyer) {
        TicketLot listing = ticketRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new NotFoundException("Билет не найден"));
        if (listing.getStatus() == TicketStatus.COMPLETED && isBuyer(listing, buyer)) {
            return listing;
        }
        return null;
    }

    private ListingHold createHoldTx(Long listingId, User buyer) {
        TicketLot listing = loadListingForPurchase(listingId, buyer);

        Instant now = Instant.now();
        Optional<ListingHold> existing = listingHoldRepository.findByListingId(listingId);
        if (existing.isPresent()) {
            ListingHold hold = existing.get();
            boolean active = hold.getHoldUntil() != null && hold.getHoldUntil().isAfter(now);

            if (active) {
                if (isHoldOwner(hold, buyer)) {
                    return hold;
                }
                throw new ConflictException("Билет зарезервирован другим покупателем");
            }

            listingHoldRepository.delete(hold);
            listingHoldRepository.flush();
        }

        Instant holdUntil = now.plusSeconds(DEFAULT_HOLD_SECONDS);
        try {
            ListingHold created = new ListingHold(listing, buyer, holdUntil);
            return listingHoldRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Билет зарезервирован другим покупателем");
        }
    }

    private void cancelHoldTx(Long listingId, User buyer) {
        Optional<ListingHold> existing = listingHoldRepository.findByListingId(listingId);
        if (existing.isEmpty()) {
            return;
        }

        ListingHold hold = existing.get();
        if (!isHoldOwner(hold, buyer)) {
            throw new ConflictException("Нельзя отменить резерв, созданный другим пользователем");
        }

        listingHoldRepository.delete(hold);
    }

    private StartProcessingResult startProcessingTx(Long listingId, User buyer, String idempotencyKey) {
        TicketLot listing = loadListingForPurchase(listingId, buyer);

        if (idempotencyKey != null) {
            Optional<PurchaseOrder> existing = purchaseOrderRepository.findByListingIdAndBuyerIdAndIdempotencyKey(
                    listingId,
                    buyer.getId(),
                    idempotencyKey
            );
            if (existing.isPresent()) {
                PurchaseOrder order = existing.get();
                if (order.getStatus() == PurchaseOrderStatus.COMPLETED && listing.getStatus() == TicketStatus.COMPLETED && isBuyer(listing, buyer)) {
                    return StartProcessingResult.completed(listing);
                }
                throw new ConflictException("Покупка с таким Idempotency-Key уже обрабатывалась; проверьте статус заказа перед повтором");
            }
        }

        List<PurchaseOrder> activeOrders = purchaseOrderRepository.findAllByListingIdAndStatusIn(
                listingId,
                List.of(
                        PurchaseOrderStatus.CREATED,
                        PurchaseOrderStatus.PAYMENT_AUTHORIZED,
                        PurchaseOrderStatus.PROCESSING_REISSUE,
                        PurchaseOrderStatus.WAITING_MANUAL_REISSUE
                )
        );
        if (!activeOrders.isEmpty()) {
            throw new ConflictException("По этому объявлению уже есть активная покупка");
        }

        ListingHold hold = createHoldTx(listingId, buyer);
        Instant now = Instant.now();
        if (hold.getHoldUntil() == null || !hold.getHoldUntil().isAfter(now)) {
            throw new ConflictException("Резерв истёк");
        }

        listing.setBuyer(buyer);
        listingStatusHistoryService.transition(listing, TicketStatus.PROCESSING, "Покупка начата, платёж условно авторизован", buyer);

        PurchaseOrder order = new PurchaseOrder(listing, buyer, idempotencyKey);
        order.setPaymentStatus(PaymentStatus.AUTHORIZED);
        order.setStatus(requiresManualReissue(listing) ? PurchaseOrderStatus.WAITING_MANUAL_REISSUE : PurchaseOrderStatus.PROCESSING_REISSUE);
        PurchaseOrder savedOrder = purchaseOrderRepository.saveAndFlush(order);

        return new StartProcessingResult(savedOrder.getId(), requiresManualReissue(listing), null);
    }

    private TicketLot markWaitingManualReissueTx(Long listingId, User buyer, Long orderId) {
        TicketLot listing = ticketRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new NotFoundException("Билет не найден"));

        ensureProcessingBuyer(listing, buyer);
        updateOrder(orderId, PurchaseOrderStatus.WAITING_MANUAL_REISSUE, PaymentStatus.AUTHORIZED, null, null, false);
        listingStatusHistoryService.recordStatus(
                listing,
                TicketStatus.PROCESSING,
                TicketStatus.PROCESSING,
                MANUAL_REISSUE_WAITING_REASON,
                null
        );
        return listing;
    }

    private void markPartnerOperationStartedTx(Long orderId) {
        PurchaseOrder order = purchaseOrderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Заказ не найден"));
        order.setPartnerOperationId("REISSUE-" + order.getListing().getId() + "-" + order.getId());
        order.setStatus(PurchaseOrderStatus.PROCESSING_REISSUE);
        purchaseOrderRepository.saveAndFlush(order);
    }

    private TicketLot completePurchaseTx(Long listingId, User buyer, Long orderId, String reissuedTicketUid) {
        TicketLot listing = ticketRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new NotFoundException("Билет не найден"));

        if (listing.getStatus() == TicketStatus.COMPLETED) {
            if (isBuyer(listing, buyer)) {
                updateOrder(orderId, PurchaseOrderStatus.COMPLETED, PaymentStatus.CAPTURED, null, Instant.now(), true);
                return listing;
            }
            throw new ConflictException("Билет уже продан другому покупателю");
        }

        ensureProcessingBuyer(listing, buyer);

        ListingHold hold = listingHoldRepository.findByListingIdAndHoldUntilAfter(listingId, Instant.now())
                .orElseThrow(() -> new ConflictException("Нет активного резерва для этого объявления"));

        if (!isHoldOwner(hold, buyer)) {
            throw new ConflictException("Это объявление зарезервировано другим покупателем");
        }

        listing.setReissuedTicketUid(reissuedTicketUid);
        listingStatusHistoryService.recordStatus(listing, listing.getStatus(), listing.getStatus(), "Билет перевыпущен партнёром", null);
        TicketLot saved = listingStatusHistoryService.transition(listing, TicketStatus.COMPLETED, "Покупка завершена, платёж захвачен", buyer);

        updateOrder(orderId, PurchaseOrderStatus.COMPLETED, PaymentStatus.CAPTURED, null, Instant.now(), true);
        listingHoldRepository.deleteByListingId(listingId);

        return saved;
    }

    private TicketLot failPurchaseTx(Long listingId, User buyer, Long orderId, String reason) {
        TicketLot listing = ticketRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new NotFoundException("Билет не найден"));

        if (listing.getStatus() == TicketStatus.COMPLETED) {
            updateOrder(orderId, PurchaseOrderStatus.COMPLETED, PaymentStatus.CAPTURED, null, Instant.now(), true);
            return listing;
        }

        if (listing.getBuyer() == null || listing.getBuyer().getId() == null || !listing.getBuyer().getId().equals(buyer.getId())) {
            listing.setBuyer(buyer);
        }

        updateOrder(orderId, PurchaseOrderStatus.REFUND_REQUIRED, PaymentStatus.REFUND_REQUIRED, reason, null, true);
        TicketLot saved = listingStatusHistoryService.transition(listing, TicketStatus.FAILED, reason + "; требуется возврат условно авторизованного платежа", null);
        listingHoldRepository.deleteByListingId(listingId);
        return saved;
    }

    private boolean requiresManualReissue(TicketLot listing) {
        Organizer organizer = listing.getOrganizer();
        return organizer != null && organizer.getVerificationMode() == OrganizerVerificationMode.MANUAL;
    }

    private ReissueResult reissueTicketWithPartner(Long listingId, User buyer) {
        TicketLot listing = ticketRepository.findById(listingId)
                .orElseThrow(() -> new NotFoundException("Билет не найден"));

        String organizerCode = null;
        if (listing.getOrganizer() != null) {
            organizerCode = listing.getOrganizer().getApiKey();
        }
        if (organizerCode == null || organizerCode.isBlank()) {
            organizerCode = partnerOrganizerCodeMapper.resolveOrganizerCode(listing.getOrganizerName()).orElse(null);
        }
        if (organizerCode == null || organizerCode.isBlank()) {
            return ReissueResult.failed("Перевыпуск у партнёра не выполнен: организатор не поддерживается");
        }

        try {
            PartnerTicketReissueResponse response = partnerApiClient.reissueTicket(
                    organizerCode,
                    listing.getUid(),
                    buyer.getEmail()
            );

            if (!response.success()) {
                return ReissueResult.failed("Перевыпуск у партнёра не выполнен: " + failureReason(response.reason()));
            }

            return ReissueResult.success(response.newTicketUid());
        } catch (PartnerIntegrationException ex) {
            return ReissueResult.failed("Перевыпуск у партнёра не выполнен: ошибка интеграции");
        }
    }

    private void updateOrder(
            Long orderId,
            PurchaseOrderStatus orderStatus,
            PaymentStatus paymentStatus,
            String failureReason,
            Instant completedAt,
            boolean flush
    ) {
        if (orderId == null) {
            return;
        }
        PurchaseOrder order = purchaseOrderRepository.findById(orderId)
                .orElseThrow(() -> new NotFoundException("Заказ не найден"));
        order.setStatus(orderStatus);
        order.setPaymentStatus(paymentStatus);
        order.setFailureReason(failureReason);
        if (completedAt != null) {
            order.setCompletedAt(completedAt);
        }
        if (flush) {
            purchaseOrderRepository.saveAndFlush(order);
        } else {
            purchaseOrderRepository.save(order);
        }
    }

    private void ensureProcessingBuyer(TicketLot listing, User buyer) {
        if (listing.getStatus() != TicketStatus.PROCESSING) {
            throw new BusinessRuleException("Покупка не находится в обработке");
        }
        if (!isBuyer(listing, buyer)) {
            throw new ConflictException("Это объявление обрабатывается для другого покупателя");
        }
    }

    private boolean isBuyer(TicketLot listing, User buyer) {
        return buyer != null
                && buyer.getId() != null
                && listing.getBuyer() != null
                && listing.getBuyer().getId() != null
                && listing.getBuyer().getId().equals(buyer.getId());
    }

    private boolean isHoldOwner(ListingHold hold, User buyer) {
        return buyer != null
                && buyer.getId() != null
                && hold.getBuyer() != null
                && hold.getBuyer().getId() != null
                && hold.getBuyer().getId().equals(buyer.getId());
    }

    private String failureReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "причина неизвестна";
        }
        return reason.trim();
    }

    private TicketLot loadListingForPurchase(Long listingId, User buyer) {
        TicketLot listing = ticketRepository.findByIdForUpdate(listingId)
                .orElseThrow(() -> new NotFoundException("Билет не найден"));

        if (listing.getStatus() == TicketStatus.COMPLETED) {
            if (isBuyer(listing, buyer)) {
                throw new ConflictException("Покупка уже завершена для этого пользователя");
            }
            throw new BusinessRuleException("Билет уже продан");
        }

        if (listing.getStatus() == TicketStatus.PROCESSING) {
            throw new ConflictException("Покупка этого билета уже обрабатывается");
        }

        if (listing.getStatus() != TicketStatus.PENDING_RECIPIENT) {
            throw new BusinessRuleException("Билет недоступен для покупки");
        }

        if (!listing.hasTicketFile()) {
            throw new BusinessRuleException("Билет недоступен для покупки: файл билета не загружен");
        }

        if (listing.getSeller() != null && listing.getSeller().getId() != null && listing.getSeller().getId().equals(buyer.getId())) {
            throw new BusinessRuleException("Нельзя купить свой собственный билет");
        }

        LocalDateTime now = LocalDateTime.now();
        if (listing.getEventDate() != null && listing.getEventDate().isBefore(now)) {
            throw new BusinessRuleException("Мероприятие уже прошло");
        }

        Organizer organizer = listing.getOrganizer();
        if (organizer == null) {
            throw new BusinessRuleException("У объявления не выбран зарегистрированный организатор");
        }
        if (organizer.isBanned()) {
            throw new BusinessRuleException("Организатор этого билета заблокирован");
        }

        return listing;
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (!SAFE_IDEMPOTENCY_KEY.matcher(normalized).matches()) {
            throw new BusinessRuleException("Idempotency-Key должен быть от 8 до 128 символов и содержать только буквы, цифры, точку, подчёркивание, дефис или двоеточие");
        }
        return normalized;
    }

    private record StartProcessingResult(Long orderId, boolean manual, TicketLot completedListing) {
        private static StartProcessingResult completed(TicketLot listing) {
            return new StartProcessingResult(null, false, listing);
        }
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
