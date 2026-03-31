package ru.ticketswap.auth;

import ru.ticketswap.common.UnauthorizedException;

public class ExpiredPasswordResetTokenException extends UnauthorizedException {
    public ExpiredPasswordResetTokenException(String message) {
        super(message);
    }
}
