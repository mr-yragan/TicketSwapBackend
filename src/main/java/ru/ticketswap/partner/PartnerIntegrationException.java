package ru.ticketswap.partner;

public class PartnerIntegrationException extends RuntimeException {

    public PartnerIntegrationException(String message) {
        super(message);
    }

    public PartnerIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
