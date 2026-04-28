package ru.ticketswap.payment;

public record PaymentRefundRequest(
        String paymentOperationId,
        String reason
) {
}
