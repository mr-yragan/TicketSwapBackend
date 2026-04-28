package ru.ticketswap.partner;

public record PartnerTicketReissueRequest(
        String originalTicketUid,
        String buyerEmail,
        String eventId,
        String operationId
) {
}
