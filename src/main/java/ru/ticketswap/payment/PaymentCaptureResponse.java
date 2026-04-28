package ru.ticketswap.payment;

public record PaymentCaptureResponse(
        boolean captured,
        String paymentOperationId,
        String reason
) {
}
