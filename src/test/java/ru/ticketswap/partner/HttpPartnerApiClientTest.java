package ru.ticketswap.partner;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.ticketswap.config.ApplicationConfig;
import ru.ticketswap.config.TicketSwapProperties;
import ru.ticketswap.user.UserIdentityService;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpPartnerApiClientTest {

    private HttpServer server;
    private ExecutorService executor;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    void verifyTicketUsesBaseUrlFromConfigurationAndParsesSuccessfulResponse() throws Exception {
        AtomicReference<String> capturedMethod = new AtomicReference<>();
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();

        startServer(exchange -> {
            capturedMethod.set(exchange.getRequestMethod());
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedBody.set(readBody(exchange));
            writeJson(exchange, 200, """
                    {"valid":true,"ticketUid":"ticket-123","organizerCode":"org1","reason":"ok"}
                    """);
        });

        HttpPartnerApiClient client = new HttpPartnerApiClient(createConfiguredRestClient(500, 500));

        PartnerTicketVerifyResponse response = client.verifyTicket("org1", "ticket-123");

        assertEquals("POST", capturedMethod.get());
        assertEquals("/api/mock/partners/org1/tickets/verify", capturedPath.get());
        assertTrue(capturedBody.get().contains("\"ticketUid\":\"ticket-123\""));
        assertTrue(response.valid());
        assertEquals("ticket-123", response.ticketUid());
        assertEquals("org1", response.organizerCode());
    }

    @Test
    void verifyTicketWrapsBadRequestAsPartnerIntegrationException() throws Exception {
        startServer(exchange -> writeJson(exchange, 400, """
                {"error":"bad request"}
                """));

        HttpPartnerApiClient client = new HttpPartnerApiClient(createConfiguredRestClient(500, 500));

        PartnerIntegrationException ex = assertThrows(
                PartnerIntegrationException.class,
                () -> client.verifyTicket("org1", "ticket-123")
        );

        assertTrue(ex.getMessage().contains("400"));
    }

    @Test
    void verifyTicketWrapsNotFoundAsPartnerIntegrationException() throws Exception {
        startServer(exchange -> writeJson(exchange, 404, """
                {"error":"not found"}
                """));

        HttpPartnerApiClient client = new HttpPartnerApiClient(createConfiguredRestClient(500, 500));

        PartnerIntegrationException ex = assertThrows(
                PartnerIntegrationException.class,
                () -> client.verifyTicket("org1", "ticket-123")
        );

        assertTrue(ex.getMessage().contains("404"));
    }

    @Test
    void verifyTicketFailsOnMalformedJson() throws Exception {
        startServer(exchange -> writeJson(exchange, 200, """
                {"valid":true,"ticketUid":
                """));

        HttpPartnerApiClient client = new HttpPartnerApiClient(createConfiguredRestClient(500, 500));

        PartnerIntegrationException ex = assertThrows(
                PartnerIntegrationException.class,
                () -> client.verifyTicket("org1", "ticket-123")
        );

        assertTrue(ex.getMessage().contains("request failed"));
    }

    @Test
    void verifyTicketFailsOnUnexpectedResponseContract() throws Exception {
        startServer(exchange -> writeJson(exchange, 200, """
                {"valid":true,"ticketUid":"ticket-123"}
                """));

        HttpPartnerApiClient client = new HttpPartnerApiClient(createConfiguredRestClient(500, 500));

        PartnerIntegrationException ex = assertThrows(
                PartnerIntegrationException.class,
                () -> client.verifyTicket("org1", "ticket-123")
        );

        assertTrue(ex.getMessage().contains("invalid organizerCode"));
    }

    @Test
    void verifyTicketFailsOnTimeout() throws Exception {
        startServer(exchange -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            writeJson(exchange, 200, """
                    {"valid":true,"ticketUid":"ticket-123","organizerCode":"org1"}
                    """);
        });

        HttpPartnerApiClient client = new HttpPartnerApiClient(createConfiguredRestClient(100, 50));

        PartnerIntegrationException ex = assertThrows(
                PartnerIntegrationException.class,
                () -> client.verifyTicket("org1", "ticket-123")
        );

        assertTrue(ex.getMessage().contains("request failed"));
    }

    @Test
    void reissueTicketUsesBaseUrlFromConfigurationAndParsesSuccessfulResponse() throws Exception {
        AtomicReference<String> capturedMethod = new AtomicReference<>();
        AtomicReference<String> capturedPath = new AtomicReference<>();
        AtomicReference<String> capturedBody = new AtomicReference<>();

        startServer(exchange -> {
            capturedMethod.set(exchange.getRequestMethod());
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedBody.set(readBody(exchange));
            writeJson(exchange, 200, """
                    {"success":true,"originalTicketUid":"ticket-123","newTicketUid":"REISSUED-org1-ticket-123","organizerCode":"org1"}
                    """);
        });

        HttpPartnerApiClient client = new HttpPartnerApiClient(createConfiguredRestClient(500, 500));

        PartnerTicketReissueResponse response = client.reissueTicket("org1", "ticket-123", "buyer@example.com");

        assertEquals("POST", capturedMethod.get());
        assertEquals("/api/mock/partners/org1/tickets/reissue", capturedPath.get());
        assertTrue(capturedBody.get().contains("\"originalTicketUid\":\"ticket-123\""));
        assertTrue(capturedBody.get().contains("\"buyerEmail\":\"buyer@example.com\""));
        assertTrue(response.success());
        assertEquals("REISSUED-org1-ticket-123", response.newTicketUid());
        assertEquals("org1", response.organizerCode());
    }

    @Test
    void reissueTicketAllowsBusinessFailureResponse() throws Exception {
        startServer(exchange -> writeJson(exchange, 200, """
                {"success":false,"originalTicketUid":"ticket-FAIL","organizerCode":"org1","reason":"Mock reissue failed"}
                """));

        HttpPartnerApiClient client = new HttpPartnerApiClient(createConfiguredRestClient(500, 500));

        PartnerTicketReissueResponse response = client.reissueTicket("org1", "ticket-FAIL", "buyer@example.com");

        assertEquals(false, response.success());
        assertEquals("Mock reissue failed", response.reason());
    }

    @Test
    void reissueTicketFailsOnUnexpectedSuccessfulResponseContract() throws Exception {
        startServer(exchange -> writeJson(exchange, 200, """
                {"success":true,"originalTicketUid":"ticket-123","organizerCode":"org1"}
                """));

        HttpPartnerApiClient client = new HttpPartnerApiClient(createConfiguredRestClient(500, 500));

        PartnerIntegrationException ex = assertThrows(
                PartnerIntegrationException.class,
                () -> client.reissueTicket("org1", "ticket-123", "buyer@example.com")
        );

        assertTrue(ex.getMessage().contains("invalid newTicketUid"));
    }

    private void startServer(HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.start();
    }

    private org.springframework.web.client.RestClient createConfiguredRestClient(long connectTimeoutMs, long readTimeoutMs) {
        TicketSwapProperties properties = new TicketSwapProperties();
        properties.getPartnerApi().setBaseUrl("http://localhost:" + server.getAddress().getPort());
        properties.getPartnerApi().setConnectTimeoutMs(connectTimeoutMs);
        properties.getPartnerApi().setReadTimeoutMs(readTimeoutMs);

        ApplicationConfig applicationConfig = new ApplicationConfig(
                Mockito.mock(UserIdentityService.class),
                properties
        );

        return applicationConfig.partnerApiRestClient();
    }

    private String readBody(HttpExchange exchange) throws IOException {
        return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(bytes);
        }
    }

    @FunctionalInterface
    private interface HttpHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
