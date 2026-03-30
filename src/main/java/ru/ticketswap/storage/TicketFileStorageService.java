package ru.ticketswap.storage;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.ticketswap.common.BusinessRuleException;
import ru.ticketswap.common.NotFoundException;
import ru.ticketswap.config.TicketSwapProperties;
import ru.ticketswap.ticket.TicketLot;
import ru.ticketswap.ticket.TicketRepository;
import ru.ticketswap.ticket.dto.TicketFileDownloadUrlResponse;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class TicketFileStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg"
    );

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".pdf", ".png", ".jpg", ".jpeg");

    private final MinioClient minioClient;
    private final TicketRepository ticketRepository;
    private final TicketSwapProperties.Storage.S3 properties;

    public TicketFileStorageService(
            MinioClient minioClient,
            TicketRepository ticketRepository,
            TicketSwapProperties ticketSwapProperties
    ) {
        this.minioClient = minioClient;
        this.ticketRepository = ticketRepository;
        this.properties = ticketSwapProperties.getStorage().getS3();
    }

    public TicketLot uploadTicketFile(TicketLot ticket, MultipartFile file) {
        validate(file);

        String objectKey = buildObjectKey(ticket.getId(), file.getOriginalFilename());
        String previousObjectKey = ticket.getTicketFileObjectKey();

        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(resolveContentType(file))
                            .build()
            );
        } catch (Exception ex) {
            throw new TicketFileStorageException("Failed to upload ticket file", ex);
        }

        try {
            ticket.updateTicketFile(
                    objectKey,
                    file.getOriginalFilename(),
                    resolveContentType(file),
                    file.getSize()
            );
            TicketLot saved = ticketRepository.saveAndFlush(ticket);
            if (previousObjectKey != null && !previousObjectKey.isBlank() && !previousObjectKey.equals(objectKey)) {
                deleteObjectQuietly(previousObjectKey);
            }
            return saved;
        } catch (RuntimeException ex) {
            deleteObjectQuietly(objectKey);
            throw ex;
        }
    }

    public TicketFileDownloadUrlResponse createDownloadUrl(TicketLot ticket) {
        if (!ticket.hasTicketFile()) {
            throw new NotFoundException("Ticket file not found");
        }

        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(properties.getBucket())
                            .object(ticket.getTicketFileObjectKey())
                            .expiry(
                                    properties.getPresignedGetExpiryMinutes(),
                                    TimeUnit.MINUTES
                            )
                            .build()
            );

            Instant expiresAt = Instant.now().plusSeconds(properties.getPresignedGetExpiryMinutes() * 60L);
            return new TicketFileDownloadUrlResponse(
                    url,
                    expiresAt,
                    ticket.getTicketFileOriginalName(),
                    ticket.getTicketFileContentType(),
                    ticket.getTicketFileSizeBytes()
            );
        } catch (Exception ex) {
            throw new TicketFileStorageException("Failed to create ticket file download URL", ex);
        }
    }

    public void deleteTicketFile(TicketLot ticket) {
        String objectKey = ticket.getTicketFileObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        deleteObjectQuietly(objectKey);
        ticket.clearTicketFile();
        ticketRepository.saveAndFlush(ticket);
    }

    public void deleteFileQuietly(TicketLot ticket) {
        if (ticket == null) {
            return;
        }
        String objectKey = ticket.getTicketFileObjectKey();
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        deleteObjectQuietly(objectKey);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Ticket file is required");
        }

        String contentType = resolveContentType(file);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessRuleException("Only PDF, PNG and JPG files are allowed");
        }

        String originalName = file.getOriginalFilename();
        if (!hasAllowedExtension(originalName)) {
            throw new BusinessRuleException("Ticket file must have .pdf, .png, .jpg or .jpeg extension");
        }
    }

    private String buildObjectKey(Long ticketId, String originalFilename) {
        String extension = extractExtension(originalFilename);
        return "tickets/%d/%s%s".formatted(ticketId, UUID.randomUUID(), extension);
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "";
        }
        String lower = originalFilename.toLowerCase(Locale.ROOT);
        int dotIndex = lower.lastIndexOf('.');
        if (dotIndex < 0) {
            return "";
        }
        return lower.substring(dotIndex);
    }

    private boolean hasAllowedExtension(String originalFilename) {
        String extension = extractExtension(originalFilename);
        return ALLOWED_EXTENSIONS.contains(extension);
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType != null) {
            contentType = contentType.split(";")[0].trim();
            if ("image/jpg".equalsIgnoreCase(contentType)) {
                contentType = "image/jpeg";
            }
        }
        if (contentType == null || contentType.isBlank()) {
            String extension = extractExtension(file.getOriginalFilename());
            if (".pdf".equals(extension)) {
                return "application/pdf";
            }
            if (".png".equals(extension)) {
                return "image/png";
            }
            if (".jpg".equals(extension) || ".jpeg".equals(extension)) {
                return "image/jpeg";
            }
        }
        return contentType == null ? "application/octet-stream" : contentType.toLowerCase(Locale.ROOT);
    }

    private void deleteObjectQuietly(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectKey)
                            .build()
            );
        } catch (Exception ignored) {
        }
    }
}
