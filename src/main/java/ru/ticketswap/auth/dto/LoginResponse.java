package ru.ticketswap.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        boolean requiresTwoFactor,
        String token,
        String twoFactorChallengeId,
        Instant twoFactorExpiresAt,
        String message
) {

    public static LoginResponse authenticated(String token) {
        return new LoginResponse(false, token, null, null, null);
    }

    public static LoginResponse twoFactorRequired(String challengeId, Instant expiresAt) {
        return new LoginResponse(
                true,
                null,
                challengeId,
                expiresAt,
                "Код подтверждения отправлен на почту"
        );
    }
}
