package ru.ticketswap.storage;

public class TicketFileStorageException extends RuntimeException {

    public TicketFileStorageException(String message) {
        super(message);
    }
    public TicketFileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
