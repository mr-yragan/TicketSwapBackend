package ru.ticketswap.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import ru.ticketswap.config.TicketSwapProperties;
import ru.ticketswap.ticket.TicketFileRepository;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class TicketFileStorageServiceTest {

    @Test
    void serviceCanBeCreatedWithSeparateInternalAndPublicClients() {
        TicketSwapProperties properties = new TicketSwapProperties();
        properties.getStorage().getS3().setBucket("ticketswap-ticket-files");
        properties.getStorage().getS3().setPresignedGetExpiryMinutes(15);

        MinioClient internalClient = MinioClient.builder()
                .endpoint("http://localhost:9000")
                .credentials("minio-access-key", "minio-secret-key")
                .build();
        MinioClient publicClient = MinioClient.builder()
                .endpoint("http://localhost:9000")
                .credentials("minio-access-key", "minio-secret-key")
                .build();

        assertDoesNotThrow(() -> new TicketFileStorageService(
                internalClient,
                publicClient,
                Mockito.mock(TicketFileRepository.class),
                properties
        ));
    }
}
