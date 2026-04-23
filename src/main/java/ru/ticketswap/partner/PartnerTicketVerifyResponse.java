package ru.ticketswap.partner;

public record PartnerTicketVerifyResponse(
        boolean valid,
        String ticketUid,
        String organizerCode,
        String reason
) {
}
