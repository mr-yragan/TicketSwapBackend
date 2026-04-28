package ru.ticketswap.payment;

public record PaymentCaptureRequest(
        String paymentOperationId
) {
}
