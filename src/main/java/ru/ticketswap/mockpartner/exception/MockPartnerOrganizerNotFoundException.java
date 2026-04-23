package ru.ticketswap.mockpartner.exception;

import ru.ticketswap.common.NotFoundException;

public class MockPartnerOrganizerNotFoundException extends NotFoundException {

    public MockPartnerOrganizerNotFoundException(String organizerCode) {
        super("Organizer not found: " + organizerCode);
    }
}
