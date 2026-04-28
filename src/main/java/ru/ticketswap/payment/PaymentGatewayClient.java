package ru.ticketswap.payment;

import java.math.BigDecimal;

public interface PaymentGatewayClient {

    PaymentAuthorizeResponse authorize(String orderReference, BigDecimal amount, String currency, String buyerEmail, String idempotencyKey);

    PaymentCaptureResponse capture(String paymentOperationId);

    PaymentRefundResponse refund(String paymentOperationId, String reason);
}
