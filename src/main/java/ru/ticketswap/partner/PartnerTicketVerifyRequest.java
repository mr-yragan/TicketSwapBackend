package ru.ticketswap.partner;

public record PartnerTicketVerifyRequest(
        String ticketUid,
        String eventId
) {
}
