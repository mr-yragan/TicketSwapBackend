package ru.ticketswap.auth;

import ru.ticketswap.common.UnauthorizedException;

public class InvalidTwoFactorCodeException extends UnauthorizedException {
    public InvalidTwoFactorCodeException(String message) {
        super(message);
    }
}
