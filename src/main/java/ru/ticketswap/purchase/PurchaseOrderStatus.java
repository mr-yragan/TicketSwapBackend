package ru.ticketswap.purchase;

public enum PurchaseOrderStatus {
    CREATED,
    PAYMENT_AUTHORIZED,
    PROCESSING_REISSUE,
    WAITING_MANUAL_REISSUE,
    COMPLETED,
    FAILED,
    REFUND_REQUIRED,
    REFUNDED
}
