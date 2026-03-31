package ru.ticketswap.auth;

import ru.ticketswap.common.UnauthorizedException;

public class InvalidPasswordResetTokenException extends UnauthorizedException {
    public InvalidPasswordResetTokenException(String message) {
        super(message);
    }
}
