package ru.ticketswap.partner;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpPartnerApiClient implements PartnerApiClient {

    private final RestClient restClient;

    public HttpPartnerApiClient(@Qualifier("partnerApiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public PartnerTicketVerifyResponse verifyTicket(String organizerCode, String ticketUid) {
        validateRequiredArgument("organizerCode", organizerCode);
        validateRequiredArgument("ticketUid", ticketUid);

        try {
            PartnerTicketVerifyResponse response = restClient.post()
                    .uri("/api/mock/partners/{organizerCode}/tickets/verify", organizerCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PartnerTicketVerifyRequest(ticketUid))
                    .retrieve()
                    .body(PartnerTicketVerifyResponse.class);

            return validateResponse(response);
        } catch (HttpClientErrorException.BadRequest ex) {
            throw new PartnerIntegrationException("Partner API returned 400 Bad Request", ex);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new PartnerIntegrationException("Partner API returned 404 Not Found", ex);
        } catch (HttpStatusCodeException ex) {
            throw new PartnerIntegrationException(
                    "Partner API returned unexpected status: " + ex.getStatusCode().value(),
                    ex
            );
        } catch (RestClientException ex) {
            throw new PartnerIntegrationException("Partner API request failed", ex);
        }
    }

    @Override
    public PartnerTicketReissueResponse reissueTicket(String organizerCode, String originalTicketUid, String buyerEmail) {
        validateRequiredArgument("organizerCode", organizerCode);
        validateRequiredArgument("originalTicketUid", originalTicketUid);
        validateRequiredArgument("buyerEmail", buyerEmail);

        try {
            PartnerTicketReissueResponse response = restClient.post()
                    .uri("/api/mock/partners/{organizerCode}/tickets/reissue", organizerCode)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PartnerTicketReissueRequest(originalTicketUid, buyerEmail))
                    .retrieve()
                    .body(PartnerTicketReissueResponse.class);

            return validateReissueResponse(response);
        } catch (HttpClientErrorException.BadRequest ex) {
            throw new PartnerIntegrationException("Partner API returned 400 Bad Request", ex);
        } catch (HttpClientErrorException.NotFound ex) {
            throw new PartnerIntegrationException("Partner API returned 404 Not Found", ex);
        } catch (HttpStatusCodeException ex) {
            throw new PartnerIntegrationException(
                    "Partner API returned unexpected status: " + ex.getStatusCode().value(),
                    ex
            );
        } catch (RestClientException ex) {
            throw new PartnerIntegrationException("Partner API request failed", ex);
        }
    }

    private void validateRequiredArgument(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    private PartnerTicketVerifyResponse validateResponse(PartnerTicketVerifyResponse response) {
        if (response == null) {
            throw new PartnerIntegrationException("Partner API returned empty body");
        }
        if (response.ticketUid() == null || response.ticketUid().isBlank()) {
            throw new PartnerIntegrationException("Partner API returned invalid ticketUid");
        }
        if (response.organizerCode() == null || response.organizerCode().isBlank()) {
            throw new PartnerIntegrationException("Partner API returned invalid organizerCode");
        }
        return response;
    }

    private PartnerTicketReissueResponse validateReissueResponse(PartnerTicketReissueResponse response) {
        if (response == null) {
            throw new PartnerIntegrationException("Partner API returned empty body");
        }
        if (response.originalTicketUid() == null || response.originalTicketUid().isBlank()) {
            throw new PartnerIntegrationException("Partner API returned invalid originalTicketUid");
        }
        if (response.organizerCode() == null || response.organizerCode().isBlank()) {
            throw new PartnerIntegrationException("Partner API returned invalid organizerCode");
        }
        if (response.success() && (response.newTicketUid() == null || response.newTicketUid().isBlank())) {
            throw new PartnerIntegrationException("Partner API returned invalid newTicketUid");
        }
        return response;
    }
}
