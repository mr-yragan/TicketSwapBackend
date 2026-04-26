package ru.ticketswap.user;

public class DuplicateUserIdentityException extends IllegalStateException {

    public DuplicateUserIdentityException(String source) {
        super("Найдено несколько пользователей для " + source);
    }
}