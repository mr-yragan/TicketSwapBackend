package ru.ticketswap.mockpartner.dto;

public record MockTicketReissueRequest(
        String originalTicketUid,
        String buyerEmail
) {
}
