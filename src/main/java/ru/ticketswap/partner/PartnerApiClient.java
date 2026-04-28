package ru.ticketswap.partner;

public interface PartnerApiClient {

    PartnerTicketVerifyResponse verifyTicket(String organizerCode, String ticketUid, String eventId);

    PartnerTicketReissueResponse reissueTicket(String organizerCode, String originalTicketUid, String buyerEmail, String eventId, String operationId);
}
