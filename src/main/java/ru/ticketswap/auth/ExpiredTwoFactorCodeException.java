package ru.ticketswap.auth;

import ru.ticketswap.common.UnauthorizedException;

public class ExpiredTwoFactorCodeException extends UnauthorizedException {
    public ExpiredTwoFactorCodeException(String message) {
        super(message);
    }
}
