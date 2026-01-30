package ru.ticketswap.ticket;

public enum TicketStatus {
    CREATED,
    PENDING_VALIDATION,
    PENDING_RECIPIENT,
    PROCESSING,
    COMPLETED,
    FAILED
}
