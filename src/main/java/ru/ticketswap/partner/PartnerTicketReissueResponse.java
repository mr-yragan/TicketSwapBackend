package ru.ticketswap.partner;

public record PartnerTicketReissueResponse(
        boolean success,
        String originalTicketUid,
        String newTicketUid,
        String organizerCode,
        String reason
) {
}
