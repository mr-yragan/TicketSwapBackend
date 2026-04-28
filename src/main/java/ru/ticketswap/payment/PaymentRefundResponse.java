package ru.ticketswap.payment;

public record PaymentRefundResponse(
        boolean refunded,
        String paymentOperationId,
        String reason
) {
}
