package ru.ticketswap.purchase.dto;

import ru.ticketswap.purchase.PaymentStatus;
import ru.ticketswap.purchase.PurchaseOrder;
import ru.ticketswap.purchase.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PurchaseOrderResponse(
        Long id,
        Long listingId,
        String eventName,
        Long buyerId,
        String buyerEmail,
        Long sellerId,
        String sellerEmail,
        PurchaseOrderStatus status,
        PaymentStatus paymentStatus,
        BigDecimal amount,
        String currency,
        String failureReason,
        String paymentOperationId,
        String partnerOperationId,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt,
        Instant refundedAt
) {

    public static PurchaseOrderResponse fromEntity(PurchaseOrder order, boolean includePrivateParties) {
        return new PurchaseOrderResponse(
                order.getId(),
                order.getListing() == null ? null : order.getListing().getId(),
                order.getListing() == null ? null : order.getListing().getEventName(),
                includePrivateParties && order.getBuyer() != null ? order.getBuyer().getId() : null,
                includePrivateParties && order.getBuyer() != null ? order.getBuyer().getEmail() : null,
                includePrivateParties && order.getSeller() != null ? order.getSeller().getId() : null,
                includePrivateParties && order.getSeller() != null ? order.getSeller().getEmail() : null,
                order.getStatus(),
                order.getPaymentStatus(),
                order.getAmount(),
                order.getCurrency(),
                order.getFailureReason(),
                includePrivateParties ? order.getPaymentOperationId() : null,
                includePrivateParties ? order.getPartnerOperationId() : null,
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getCompletedAt(),
                order.getRefundedAt()
        );
    }
}
