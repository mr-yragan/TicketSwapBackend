package ru.ticketswap.storage;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import ru.ticketswap.common.BusinessRuleException;
import ru.ticketswap.common.NotFoundException;
import ru.ticketswap.config.TicketSwapProperties;
import ru.ticketswap.ticket.TicketFile;
import ru.ticketswap.ticket.TicketFileRepository;
import ru.ticketswap.ticket.TicketLot;
import ru.ticketswap.ticket.TicketRepository;
import ru.ticketswap.ticket.TicketStatus;
import ru.ticketswap.ticket.dto.TicketFileDownloadUrlResponse;
import ru.ticketswap.ticket.dto.TicketFileResponse;
import ru.ticketswap.ticket.dto.TicketFilesResponse;

import java.io.IOException;
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

    private static final int MAX_FILES_PER_LISTING = 5;
    private static final int MAX_ORIGINAL_NAME_LENGTH = 180;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg"
    );

    private static final List<String> ALLOWED_EXTENSIONS = List.of(".pdf", ".png", ".jpg", ".jpeg");

    private final MinioClient minioClient;
    private final MinioClient presignMinioClient;
    private final TicketFileRepository ticketFileRepository;
    private final TicketRepository ticketRepository;
    private final TicketSwapProperties.Storage.S3 properties;

    @Autowired
    public TicketFileStorageService(
            @Qualifier("internalMinioClient") MinioClient minioClient,
            @Qualifier("publicPresignMinioClient") MinioClient presignMinioClient,
            TicketFileRepository ticketFileRepository,
            TicketRepository ticketRepository,
            TicketSwapProperties ticketSwapProperties
    ) {
        this.minioClient = minioClient;
        this.presignMinioClient = presignMinioClient;
        this.ticketFileRepository = ticketFileRepository;
        this.ticketRepository = ticketRepository;
        this.properties = ticketSwapProperties.getStorage().getS3();
    }

    TicketFileStorageService(
            MinioClient minioClient,
            MinioClient presignMinioClient,
            TicketFileRepository ticketFileRepository,
            TicketSwapProperties ticketSwapProperties
    ) {
        this(minioClient, presignMinioClient, ticketFileRepository, null, ticketSwapProperties);
    }

    public TicketFilesResponse uploadTicketFiles(TicketLot ticket, List<MultipartFile> files) {
        List<MultipartFile> normalizedFiles = normalizeFiles(files);
        if (normalizedFiles.isEmpty()) {
            throw new BusinessRuleException("Требуется хотя бы один файл билета");
        }

        int existingCount = ticket.getId() == null
                ? 0
                : ticketFileRepository.findAllByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId()).size();
        if (existingCount + normalizedFiles.size() > MAX_FILES_PER_LISTING) {
            throw new BusinessRuleException("К одному билету можно прикрепить не больше %d файлов".formatted(MAX_FILES_PER_LISTING));
        }

        List<TicketFile> uploadedEntities = new ArrayList<>();
        List<String> uploadedObjectKeys = new ArrayList<>();

        try {
            for (MultipartFile file : normalizedFiles) {
                ValidatedFile validatedFile = validate(file);
                String objectKey = buildObjectKey(ticket.getId(), validatedFile.originalName());
                uploadObject(objectKey, file, validatedFile.contentType());
                uploadedObjectKeys.add(objectKey);

                TicketFile ticketFile = new TicketFile(
                        ticket,
                        objectKey,
                        validatedFile.originalName(),
                        validatedFile.contentType(),
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

    public TicketFileDownloadUrlResponse uploadReissuedTicketFile(TicketLot ticket, MultipartFile file) {
        ValidatedFile validatedFile = validate(file);
        String objectKey = buildReissuedObjectKey(ticket.getId(), validatedFile.originalName());
        String previousObjectKey = ticket.getReissuedFileObjectKey();

        try {
            uploadObject(objectKey, file, validatedFile.contentType());
            ticket.setReissuedFileObjectKey(objectKey);
            ticket.setReissuedFileOriginalName(validatedFile.originalName());
            ticket.setReissuedFileContentType(validatedFile.contentType());
            ticket.setReissuedFileSizeBytes(file.getSize());
            ticket.setReissuedFileUploadedAt(Instant.now());
            if (ticketRepository != null) {
                ticketRepository.saveAndFlush(ticket);
            }
            deleteObjectQuietly(previousObjectKey);
            return createReissuedTicketDownloadUrl(ticket);
        } catch (RuntimeException ex) {
            deleteObjectQuietly(objectKey);
            throw ex;
        }
    }

    public TicketFileDownloadUrlResponse createReissuedTicketDownloadUrl(TicketLot ticket) {
        if (ticket.getReissuedFileObjectKey() == null || ticket.getReissuedFileObjectKey().isBlank()) {
            throw new NotFoundException("Новый файл билета ещё не загружен");
        }
        return createDownloadUrl(
                null,
                ticket.getReissuedFileObjectKey(),
                ticket.getReissuedFileOriginalName(),
                ticket.getReissuedFileContentType(),
                ticket.getReissuedFileSizeBytes()
        );
    }

    public void deleteReissuedTicketFileQuietly(TicketLot ticket) {
        if (ticket == null || ticket.getReissuedFileObjectKey() == null) {
            return;
        }
        deleteObjectQuietly(ticket.getReissuedFileObjectKey());
        ticket.setReissuedFileObjectKey(null);
        ticket.setReissuedFileOriginalName(null);
        ticket.setReissuedFileContentType(null);
        ticket.setReissuedFileSizeBytes(null);
        ticket.setReissuedFileUploadedAt(null);
        if (ticketRepository != null) {
            ticketRepository.saveAndFlush(ticket);
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
            throw new NotFoundException("Файл билета не найден");
        }
        if (files.size() > 1) {
            throw new BusinessRuleException("К билету прикреплено несколько файлов; используйте /api/tickets/{id}/files/{fileId}/download-url");
        }
        return createDownloadUrl(files.get(0));
    }

    public void deleteTicketFile(TicketLot ticket, Long fileId) {
        List<TicketFile> files = ticketFileRepository.findAllByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId());
        if (files.size() <= 1 && ticket.getStatus() != TicketStatus.FAILED) {
            throw new BusinessRuleException("Нельзя оставить объявление без файла билета");
        }
        TicketFile ticketFile = loadTicketFile(ticket, fileId);
        deleteObjectQuietly(ticketFile.getObjectKey());
        ticketFileRepository.delete(ticketFile);
        ticketFileRepository.flush();
    }

    public void deleteAllTicketFiles(TicketLot ticket) {
        if (ticket.getStatus() != TicketStatus.FAILED && ticket.getStatus() != TicketStatus.CREATED) {
            throw new BusinessRuleException("Нельзя удалить все файлы активного объявления");
        }
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
        List<TicketFile> files = ticketFileRepository.findAllByTicketIdOrderByCreatedAtAscIdAsc(ticket.getId());
        for (TicketFile file : files) {
            deleteObjectQuietly(file.getObjectKey());
        }
        if (!files.isEmpty()) {
            ticketFileRepository.deleteAll(files);
            ticketFileRepository.flush();
        }
        deleteReissuedTicketFileQuietly(ticket);
    }

    private TicketFile loadTicketFile(TicketLot ticket, Long fileId) {
        return ticketFileRepository.findByIdAndTicketId(fileId, ticket.getId())
                .orElseThrow(() -> new NotFoundException("Файл билета не найден"));
    }

    private TicketFileDownloadUrlResponse createDownloadUrl(TicketFile ticketFile) {
        return createDownloadUrl(
                ticketFile.getId(),
                ticketFile.getObjectKey(),
                ticketFile.getOriginalName(),
                ticketFile.getContentType(),
                ticketFile.getSizeBytes()
        );
    }

    private TicketFileDownloadUrlResponse createDownloadUrl(
            Long fileId,
            String objectKey,
            String originalName,
            String contentType,
            Long sizeBytes
    ) {
        try {
            String generatedUrl = presignMinioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(properties.getBucket())
                            .object(objectKey)
                            .expiry(properties.getPresignedGetExpiryMinutes(), TimeUnit.MINUTES)
                            .build()
            );

            Instant expiresAt = Instant.now().plusSeconds(properties.getPresignedGetExpiryMinutes() * 60L);
            return new TicketFileDownloadUrlResponse(
                    fileId,
                    generatedUrl,
                    expiresAt,
                    originalName,
                    contentType,
                    sizeBytes
            );
        } catch (Exception ex) {
            throw new TicketFileStorageException("Не удалось создать ссылку для скачивания файла билета", ex);
        }
    }

    private void uploadObject(String objectKey, MultipartFile file, String contentType) {
        try (InputStream inputStream = file.getInputStream()) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectKey)
                            .stream(inputStream, file.getSize(), -1)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception ex) {
            throw new TicketFileStorageException("Не удалось загрузить файл билета", ex);
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

    private ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("Файл билета обязателен");
        }

        String originalName = safeOriginalName(file);
        if (!hasAllowedExtension(originalName)) {
            throw new BusinessRuleException("Файл билета должен иметь расширение .pdf, .png, .jpg или .jpeg");
        }

        String declaredContentType = resolveDeclaredContentType(file);
        if (!ALLOWED_CONTENT_TYPES.contains(declaredContentType)) {
            throw new BusinessRuleException("Разрешены только файлы PDF, PNG и JPG");
        }

        String detectedContentType = detectContentType(file);
        if (!declaredContentType.equals(detectedContentType)) {
            throw new BusinessRuleException("Тип файла не совпадает с содержимым файла");
        }

        String extension = extractExtension(originalName);
        if (!extensionMatchesContentType(extension, detectedContentType)) {
            throw new BusinessRuleException("Расширение файла не совпадает с содержимым файла");
        }

        return new ValidatedFile(originalName, detectedContentType);
    }

    private String detectContentType(MultipartFile file) {
        byte[] header = new byte[8];
        int read;
        try (InputStream inputStream = file.getInputStream()) {
            read = inputStream.read(header);
        } catch (IOException ex) {
            throw new TicketFileStorageException("Не удалось проверить файл билета", ex);
        }

        if (read >= 4
                && header[0] == 0x25
                && header[1] == 0x50
                && header[2] == 0x44
                && header[3] == 0x46) {
            return "application/pdf";
        }

        if (read >= 8
                && (header[0] & 0xFF) == 0x89
                && header[1] == 0x50
                && header[2] == 0x4E
                && header[3] == 0x47
                && header[4] == 0x0D
                && header[5] == 0x0A
                && header[6] == 0x1A
                && header[7] == 0x0A) {
            return "image/png";
        }

        if (read >= 3
                && (header[0] & 0xFF) == 0xFF
                && (header[1] & 0xFF) == 0xD8
                && (header[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }

        throw new BusinessRuleException("Файл билета не похож на PDF, PNG или JPG");
    }

    private String buildObjectKey(Long ticketId, String originalFilename) {
        String extension = extractExtension(originalFilename);
        return "tickets/%d/%s%s".formatted(ticketId, UUID.randomUUID(), extension);
    }

    private String buildReissuedObjectKey(Long ticketId, String originalFilename) {
        String extension = extractExtension(originalFilename);
        return "tickets/%d/reissued/%s%s".formatted(ticketId, UUID.randomUUID(), extension);
    }

    private String safeOriginalName(MultipartFile file) {
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            return "ticket-file";
        }
        String normalized = originalName.replace('\\', '/');
        int slashIndex = normalized.lastIndexOf('/');
        String fileName = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
        fileName = fileName.replaceAll("[\\r\\n\\t]", "_").trim();
        if (fileName.isEmpty()) {
            return "ticket-file";
        }
        if (fileName.length() > MAX_ORIGINAL_NAME_LENGTH) {
            String extension = extractExtension(fileName);
            int baseLength = MAX_ORIGINAL_NAME_LENGTH - extension.length();
            if (baseLength < 1) {
                return fileName.substring(0, MAX_ORIGINAL_NAME_LENGTH);
            }
            return fileName.substring(0, Math.min(baseLength, fileName.length() - extension.length())) + extension;
        }
        return fileName;
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

    private boolean extensionMatchesContentType(String extension, String contentType) {
        if ("application/pdf".equals(contentType)) {
            return ".pdf".equals(extension);
        }
        if ("image/png".equals(contentType)) {
            return ".png".equals(extension);
        }
        if ("image/jpeg".equals(contentType)) {
            return ".jpg".equals(extension) || ".jpeg".equals(extension);
        }
        return false;
    }

    private String resolveDeclaredContentType(MultipartFile file) {
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
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
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

    private record ValidatedFile(String originalName, String contentType) {
    }
}
