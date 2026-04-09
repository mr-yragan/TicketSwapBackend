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

        assertDoesNotThrow(() -> new TicketFileStorageService(
                Mockito.mock(MinioClient.class),
                Mockito.mock(MinioClient.class),
                Mockito.mock(TicketFileRepository.class),
                properties
        ));
    }
}
