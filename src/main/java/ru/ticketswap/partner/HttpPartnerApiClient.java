package ru.ticketswap.partner;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import ru.ticketswap.config.TicketSwapProperties;

@Component
public class HttpPartnerApiClient implements PartnerApiClient {

    public static final String INTERNAL_TOKEN_HEADER = "X-Partner-Internal-Token";

    private final RestClient restClient;
    private final String internalToken;

    public HttpPartnerApiClient(
            @Qualifier("partnerApiRestClient") RestClient restClient,
            TicketSwapProperties properties
    ) {
        this.restClient = restClient;
        this.internalToken = properties.getPartnerApi().getInternalToken();
    }

    @Override
    public PartnerTicketVerifyResponse verifyTicket(String organizerCode, String ticketUid, String eventId) {
        validateRequiredArgument("organizerCode", organizerCode);
        validateRequiredArgument("ticketUid", ticketUid);
        validateRequiredArgument("eventId", eventId);

        try {
            PartnerTicketVerifyResponse response = restClient.post()
                    .uri("/api/partners/{organizerCode}/tickets/verify", organizerCode)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PartnerTicketVerifyRequest(ticketUid, eventId))
                    .retrieve()
                    .body(PartnerTicketVerifyResponse.class);

            return validateResponse(response);
        } catch (HttpClientErrorException.BadRequest ex) {
            throw new PartnerIntegrationException("API партнёра вернул 400 Некорректный запрос", ex);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new PartnerIntegrationException("API партнёра вернул 404 Не найдено", ex);
        } catch (HttpStatusCodeException ex) {
            throw new PartnerIntegrationException(
                    "API партнёра вернул неожиданный статус: " + ex.getStatusCode().value(),
                    ex
            );
        } catch (RestClientException ex) {
            throw new PartnerIntegrationException("Запрос к API партнёра не выполнен", ex);
        }
    }

    @Override
    public PartnerTicketReissueResponse reissueTicket(String organizerCode, String originalTicketUid, String buyerEmail, String eventId, String operationId) {
        validateRequiredArgument("organizerCode", organizerCode);
        validateRequiredArgument("originalTicketUid", originalTicketUid);
        validateRequiredArgument("buyerEmail", buyerEmail);
        validateRequiredArgument("eventId", eventId);
        validateRequiredArgument("operationId", operationId);

        try {
            PartnerTicketReissueResponse response = restClient.post()
                    .uri("/api/partners/{organizerCode}/tickets/reissue", organizerCode)
                    .header(INTERNAL_TOKEN_HEADER, internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PartnerTicketReissueRequest(originalTicketUid, buyerEmail, eventId, operationId))
                    .retrieve()
                    .body(PartnerTicketReissueResponse.class);

            return validateReissueResponse(response);
        } catch (HttpClientErrorException.BadRequest ex) {
            throw new PartnerIntegrationException("API партнёра вернул 400 Некорректный запрос", ex);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new PartnerIntegrationException("API партнёра вернул 404 Не найдено", ex);
        } catch (HttpStatusCodeException ex) {
            throw new PartnerIntegrationException(
                    "API партнёра вернул неожиданный статус: " + ex.getStatusCode().value(),
                    ex
            );
        } catch (RestClientException ex) {
            throw new PartnerIntegrationException("Запрос к API партнёра не выполнен", ex);
        }
    }

    private void validateRequiredArgument(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " не должен быть пустым");
        }
    }

    private PartnerTicketVerifyResponse validateResponse(PartnerTicketVerifyResponse response) {
        if (response == null) {
            throw new PartnerIntegrationException("API партнёра вернул пустое тело ответа");
        }
        if (response.ticketUid() == null || response.ticketUid().isBlank()) {
            throw new PartnerIntegrationException("API партнёра вернул некорректный UID билета");
        }
        if (response.organizerCode() == null || response.organizerCode().isBlank()) {
            throw new PartnerIntegrationException("API партнёра вернул некорректный код организатора");
        }
        if (response.eventId() == null || response.eventId().isBlank()) {
            throw new PartnerIntegrationException("API партнёра вернул некорректный eventId");
        }
        return response;
    }

    private PartnerTicketReissueResponse validateReissueResponse(PartnerTicketReissueResponse response) {
        if (response == null) {
            throw new PartnerIntegrationException("API партнёра вернул пустое тело ответа");
        }
        if (response.originalTicketUid() == null || response.originalTicketUid().isBlank()) {
            throw new PartnerIntegrationException("API партнёра вернул некорректный UID исходного билета");
        }
        if (response.organizerCode() == null || response.organizerCode().isBlank()) {
            throw new PartnerIntegrationException("API партнёра вернул некорректный код организатора");
        }
        if (response.eventId() == null || response.eventId().isBlank()) {
            throw new PartnerIntegrationException("API партнёра вернул некорректный eventId");
        }
        if (response.operationId() == null || response.operationId().isBlank()) {
            throw new PartnerIntegrationException("API партнёра вернул некорректный operationId");
        }
        if (response.success() && (response.newTicketUid() == null || response.newTicketUid().isBlank())) {
            throw new PartnerIntegrationException("API партнёра вернул некорректный UID нового билета");
        }
        return response;
    }
}
