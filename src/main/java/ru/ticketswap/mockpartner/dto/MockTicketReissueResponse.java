package ru.ticketswap.mockpartner.dto;

public record MockTicketReissueResponse(
        boolean success,
        String originalTicketUid,
        String newTicketUid,
        String organizerCode,
        String reason
) {

    public static MockTicketReissueResponse success(String originalTicketUid, String organizerCode, String newTicketUid) {
        return new MockTicketReissueResponse(true, originalTicketUid, newTicketUid, organizerCode, null);
    }

    public static MockTicketReissueResponse failed(String originalTicketUid, String organizerCode, String reason) {
        return new MockTicketReissueResponse(false, originalTicketUid, null, organizerCode, reason);
    }
}
