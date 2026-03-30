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
import ru.ticketswap.ticket.TicketFile;
import ru.ticketswap.ticket.TicketFileRepository;
import ru.ticketswap.ticket.TicketLot;
import ru.ticketswap.ticket.dto.TicketFileDownloadUrlResponse;
import ru.ticketswap.ticket.dto.TicketFileResponse;
import ru.ticketswap.ticket.dto.TicketFilesResponse;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
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
    private final TicketFileRepository ticketFileRepository;
    private final TicketSwapProperties.Storage.S3 properties;

    public TicketFileStorageService(
            MinioClient minioClient,
            TicketFileRepository ticketFileRepository,
            TicketSwapProperties ticketSwapProperties
    ) {
        this.minioClient = minioClient;
        this.ticketFileRepository = ticketFileRepository;
        this.properties = ticketSwapProperties.getStorage().getS3();
    }

    public TicketFilesResponse uploadTicketFiles(TicketLot ticket, List<MultipartFile> files) {
        List<MultipartFile> normalizedFiles = normalizeFiles(files);
        if (normalizedFiles.isEmpty()) {
            throw new BusinessRuleException("At least one ticket file is required");
        }

        List<TicketFile> uploadedEntities = new ArrayList<>();
        List<String> uploadedObjectKeys = new ArrayList<>();

        try {
            for (MultipartFile file : normalizedFiles) {
                validate(file);
                String objectKey = buildObjectKey(ticket.getId(), file.getOriginalFilename());
                uploadObject(objectKey, file);
                uploadedObjectKeys.add(objectKey);

                TicketFile ticketFile = new TicketFile(
                        ticket,
                        objectKey,
                        safeOriginalName(file),
                        resolveContentType(file),
                        file.getSize()
                );
                uploadedEntities.add(ticketFile);
            }

            ticketFileRepository.saveAll(uploadedEntities);
            ticketFileRepository.flush();
            return listFiles(ticket);
        } catch (RuntimeException ex) {
            uploadedObjectKeys.forEach(this::deleteObjectQuietly);
            throw ex;
        }
    }

    public TicketFilesResponse listFiles(TicketLot ticket) {
        List<TicketFileResponse> files = ticketFileRepository.findAllByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId()).stream()
                .map(TicketFileResponse::fromEntity)
                .toList();
        return new TicketFilesResponse(ticket.getId(), files.size(), files);
    }

    public TicketFileDownloadUrlResponse createDownloadUrl(TicketLot ticket, Long fileId) {
        TicketFile ticketFile = loadTicketFile(ticket, fileId);
        return createDownloadUrl(ticketFile);
    }

    public TicketFileDownloadUrlResponse createSingleDownloadUrl(TicketLot ticket) {
        List<TicketFile> files = ticketFileRepository.findAllByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId());
        if (files.isEmpty()) {
            throw new NotFoundException("Ticket file not found");
        }
        if (files.size() > 1) {
            throw new BusinessRuleException("Multiple ticket files are attached; use /api/tickets/{id}/files/{fileId}/download-url");
        }
        return createDownloadUrl(files.get(0));
    }

    public void deleteTicketFile(TicketLot ticket, Long fileId) {
        TicketFile ticketFile = loadTicketFile(ticket, fileId);
        deleteObjectQuietly(ticketFile.getObjectKey());
        ticketFileRepository.delete(ticketFile);
        ticketFileRepository.flush();
    }

    public void deleteAllTicketFiles(TicketLot ticket) {
        List<TicketFile> files = ticketFileRepository.findAllByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId());
        for (TicketFile file : files) {
            deleteObjectQuietly(file.getObjectKey());
        }
        if (!files.isEmpty()) {
            ticketFileRepository.deleteAll(files);
            ticketFileRepository.flush();
        }
    }

    public void deleteFilesQuietly(TicketLot ticket) {
        if (ticket == null || ticket.getId() == null) {
            return;
        }
        deleteAllTicketFiles(ticket);
    }

    private TicketFile loadTicketFile(TicketLot ticket, Long fileId) {
        return ticketFileRepository.findByIdAndTicketId(fileId, ticket.getId())
                .orElseThrow(() -> new NotFoundException("Ticket file not found"));
    }

    private TicketFileDownloadUrlResponse createDownloadUrl(TicketFile ticketFile) {
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(properties.getBucket())
                            .object(ticketFile.getObjectKey())
                            .expiry(properties.getPresignedGetExpiryMinutes(), TimeUnit.MINUTES)
                            .build()
            );

            Instant expiresAt = Instant.now().plusSeconds(properties.getPresignedGetExpiryMinutes() * 60L);
            return new TicketFileDownloadUrlResponse(
                    ticketFile.getId(),
                    url,
                    expiresAt,
                    ticketFile.getOriginalName(),
                    ticketFile.getContentType(),
                    ticketFile.getSizeBytes()
            );
        } catch (Exception ex) {
            throw new TicketFileStorageException("Failed to create ticket file download URL", ex);
        }
    }

    private void uploadObject(String objectKey, MultipartFile file) {
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
    }

    private List<MultipartFile> normalizeFiles(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
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

    private String safeOriginalName(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            return "ticket" + extractExtension(originalName);
        }
        return originalName;
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
