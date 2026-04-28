package ru.ticketswap.payment;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.ticketswap.config.TicketSwapProperties;

import java.math.BigDecimal;

@Component
public class HttpPaymentGatewayClient implements PaymentGatewayClient {

    public static final String INTERNAL_TOKEN_HEADER = "X-Payment-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;

    public HttpPaymentGatewayClient(
            @Qualifier("paymentApiRestClient") RestClient restClient,
            TicketSwapProperties properties
    ) {
        this.restClient = restClient;
        this.internalToken = properties.getPaymentApi().getInternalToken();
    }

    @Override
    public PaymentAuthorizeResponse authorize(String orderReference, BigDecimal amount, String currency, String buyerEmail, String idempotencyKey) {
        try {
            PaymentAuthorizeResponse response = restClient.post()
                    .uri("/api/mock/payments/authorize")
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PaymentAuthorizeRequest(orderReference, amount, currency, buyerEmail, idempotencyKey))
                    .retrieve()
                    .body(PaymentAuthorizeResponse.class);
            return requireResponse(response, "authorize");
        } catch (HttpStatusCodeException ex) {
            throw new PaymentIntegrationException("Платёжный сервис вернул статус " + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new PaymentIntegrationException("Платёжный сервис недоступен", ex);
        }
    }

    @Override
    public PaymentCaptureResponse capture(String paymentOperationId) {
        try {
            PaymentCaptureResponse response = restClient.post()
                    .uri("/api/mock/payments/capture")
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PaymentCaptureRequest(paymentOperationId))
                    .retrieve()
                    .body(PaymentCaptureResponse.class);
            if (response == null) {
                throw new PaymentIntegrationException("Платёжный сервис вернул пустой capture response");
            }
            return response;
        } catch (HttpStatusCodeException ex) {
            throw new PaymentIntegrationException("Платёжный сервис вернул статус " + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new PaymentIntegrationException("Платёжный сервис недоступен", ex);
        }
    }

    @Override
    public PaymentRefundResponse refund(String paymentOperationId, String reason) {
        try {
            PaymentRefundResponse response = restClient.post()
                    .uri("/api/mock/payments/refund")
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PaymentRefundRequest(paymentOperationId, reason))
                    .retrieve()
                    .body(PaymentRefundResponse.class);
            if (response == null) {
                throw new PaymentIntegrationException("Платёжный сервис вернул пустой refund response");
            }
            return response;
        } catch (HttpStatusCodeException ex) {
            throw new PaymentIntegrationException("Платёжный сервис вернул статус " + ex.getStatusCode().value(), ex);
        } catch (RestClientException ex) {
            throw new PaymentIntegrationException("Платёжный сервис недоступен", ex);
        }
    }

    private PaymentAuthorizeResponse requireResponse(PaymentAuthorizeResponse response, String operation) {
        if (response == null) {
            throw new PaymentIntegrationException("Платёжный сервис вернул пустой " + operation + " response");
        }
        return response;
    }
}
