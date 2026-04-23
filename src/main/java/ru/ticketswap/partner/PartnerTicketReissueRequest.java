package ru.ticketswap.partner;

public record PartnerTicketReissueRequest(
        String originalTicketUid,
        String buyerEmail
) {
}
