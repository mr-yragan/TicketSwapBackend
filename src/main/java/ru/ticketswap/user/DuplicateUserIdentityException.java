package ru.ticketswap.user;

public class DuplicateUserIdentityException extends IllegalStateException {

    public DuplicateUserIdentityException(String source) {
        super("Multiple users found for " + source);
    }
}
