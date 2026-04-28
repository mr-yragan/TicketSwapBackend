package ru.ticketswap.payment;

import java.math.BigDecimal;

public record PaymentAuthorizeRequest(
        String orderReference,
        BigDecimal amount,
        String currency,
        String buyerEmail,
        String idempotencyKey
) {
}
