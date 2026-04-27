package ru.ticketswap.mockpartner.dto;

public record MockTicketVerifyResponse(
        boolean valid,
        String ticketUid,
        String organizerCode,
        String eventId,
        String reason
) {

    public static MockTicketVerifyResponse valid(String ticketUid, String organizerCode, String eventId) {
        return new MockTicketVerifyResponse(true, ticketUid, organizerCode, eventId, null);
    }

    public static MockTicketVerifyResponse invalid(String ticketUid, String organizerCode, String eventId, String reason) {
        return new MockTicketVerifyResponse(false, ticketUid, organizerCode, eventId, reason);
    }
}
