package ru.ticketswap.payment;

public record PaymentAuthorizeResponse(
        boolean authorized,
        String paymentOperationId,
        String reason
) {
}
