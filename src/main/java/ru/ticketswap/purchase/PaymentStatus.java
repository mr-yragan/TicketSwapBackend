package ru.ticketswap.purchase;

public enum PaymentStatus {
    NOT_STARTED,
    AUTHORIZED,
    CAPTURED,
    REFUND_REQUIRED,
    REFUNDED,
    FAILED
}
