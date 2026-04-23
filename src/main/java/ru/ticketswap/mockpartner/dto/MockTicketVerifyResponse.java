package ru.ticketswap.mockpartner.dto;

public record MockTicketVerifyResponse(
        boolean valid,
        String ticketUid,
        String organizerCode,
        String reason
) {

    public static MockTicketVerifyResponse valid(String ticketUid, String organizerCode) {
        return new MockTicketVerifyResponse(true, ticketUid, organizerCode, null);
    }

    public static MockTicketVerifyResponse invalid(String ticketUid, String organizerCode, String reason) {
        return new MockTicketVerifyResponse(false, ticketUid, organizerCode, reason);
    }
}
