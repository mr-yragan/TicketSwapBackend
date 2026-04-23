package ru.ticketswap.partner;

public interface PartnerApiClient {

    PartnerTicketVerifyResponse verifyTicket(String organizerCode, String ticketUid);

    PartnerTicketReissueResponse reissueTicket(String organizerCode, String originalTicketUid, String buyerEmail);
}
