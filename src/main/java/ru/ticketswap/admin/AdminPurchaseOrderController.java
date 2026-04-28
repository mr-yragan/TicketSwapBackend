package ru.ticketswap.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.ticketswap.audit.AuditLogService;
import ru.ticketswap.common.BusinessRuleException;
import ru.ticketswap.common.NotFoundException;
import ru.ticketswap.common.UnauthorizedException;
import ru.ticketswap.notification.NotificationOutboxService;
import ru.ticketswap.payment.PaymentGatewayClient;
import ru.ticketswap.payment.PaymentIntegrationException;
import ru.ticketswap.payment.PaymentRefundResponse;
import ru.ticketswap.purchase.PaymentStatus;
import ru.ticketswap.purchase.PurchaseOrder;
import ru.ticketswap.purchase.PurchaseOrderRepository;
import ru.ticketswap.purchase.PurchaseOrderStatus;
import ru.ticketswap.purchase.dto.PurchaseOrderResponse;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserRepository;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/purchase-orders")
public class AdminPurchaseOrderController {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final AuditLogService auditLogService;
    private final NotificationOutboxService notificationOutboxService;
    private final UserRepository userRepository;

    public AdminPurchaseOrderController(
            PurchaseOrderRepository purchaseOrderRepository,
            PaymentGatewayClient paymentGatewayClient,
            AuditLogService auditLogService,
            NotificationOutboxService notificationOutboxService,
            UserRepository userRepository
    ) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.paymentGatewayClient = paymentGatewayClient;
        this.auditLogService = auditLogService;
        this.notificationOutboxService = notificationOutboxService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<List<PurchaseOrderResponse>> listPurchaseOrders() {
        List<PurchaseOrderResponse> response = purchaseOrderRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(order -> PurchaseOrderResponse.fromEntity(order, true))
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/refund/complete")
    @Transactional
    public ResponseEntity<PurchaseOrderResponse> completeRefund(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserDetails principal
    ) {
        User actor = requireUser(principal);
        PurchaseOrder order = purchaseOrderRepository.findWithPartiesById(id)
                .orElseThrow(() -> new NotFoundException("Заказ не найден"));

        if (order.getStatus() != PurchaseOrderStatus.REFUND_REQUIRED) {
            throw new BusinessRuleException("По этому заказу не требуется возврат");
        }

        String paymentOperationId = order.getPaymentOperationId();
        if (paymentOperationId == null || paymentOperationId.isBlank()) {
            throw new BusinessRuleException("У заказа нет операции платежа для возврата");
        }

        String buyerEmail = order.getBuyer() == null ? null : order.getBuyer().getEmail();
        String failureReason = order.getFailureReason();

        PaymentRefundResponse refund;
        try {
            refund = paymentGatewayClient.refund(paymentOperationId, failureReason);
        } catch (PaymentIntegrationException ex) {
            throw new BusinessRuleException("Платёжный сервис недоступен для возврата");
        }
        if (refund == null || !refund.refunded()) {
            String reason = refund == null || refund.reason() == null ? "причина неизвестна" : refund.reason();
            throw new BusinessRuleException("Платёжный сервис не подтвердил возврат: " + reason);
        }

        order.setStatus(PurchaseOrderStatus.REFUNDED);
        order.setPaymentStatus(PaymentStatus.REFUNDED);
        order.setRefundedAt(Instant.now());
        purchaseOrderRepository.saveAndFlush(order);

        auditLogService.record(actor, "REFUND_COMPLETED", "PURCHASE_ORDER", id, paymentOperationId);
        notificationOutboxService.enqueue(buyerEmail, "REFUND_COMPLETED", "Возврат платежа выполнен", "Заказ #" + id);

        PurchaseOrder saved = purchaseOrderRepository.findWithPartiesById(id)
                .orElseThrow(() -> new NotFoundException("Заказ не найден"));
        return ResponseEntity.ok(PurchaseOrderResponse.fromEntity(saved, true));
    }

    private User requireUser(UserDetails principal) {
        if (principal == null || principal.getUsername() == null) {
            throw new UnauthorizedException("Не авторизован");
        }
        return userRepository.findByEmailIgnoreCase(principal.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Не авторизован"));
    }
}
