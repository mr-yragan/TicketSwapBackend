package ru.ticketswap.ticket;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "ticket_files")
public class TicketFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", nullable = false)
    private TicketLot ticket;

    @Column(name = "object_key", nullable = false, length = 512)
    private String objectKey;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "preview_object_key", length = 512)
    private String previewObjectKey;

    @Column(name = "preview_content_type", length = 100)
    private String previewContentType;

    @Column(name = "preview_size_bytes")
    private Long previewSizeBytes;

    @Column(name = "preview_created_at")
    private Instant previewCreatedAt;

    protected TicketFile() {
    }

    public TicketFile(
            TicketLot ticket,
            String objectKey,
            String originalName,
            String contentType,
            long sizeBytes
    ) {
        this(ticket, objectKey, originalName, contentType, sizeBytes, null, null, null);
    }

    public TicketFile(
            TicketLot ticket,
            String objectKey,
            String originalName,
            String contentType,
            long sizeBytes,
            String previewObjectKey,
            String previewContentType,
            Long previewSizeBytes
    ) {
        this.ticket = ticket;
        this.objectKey = objectKey;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.createdAt = Instant.now();
        this.previewObjectKey = previewObjectKey;
        this.previewContentType = previewContentType;
        this.previewSizeBytes = previewSizeBytes;
        this.previewCreatedAt = previewObjectKey == null ? null : Instant.now();
    }

    public Long getId() {
        return id;
    }

    public TicketLot getTicket() {
        return ticket;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getPreviewObjectKey() {
        return previewObjectKey;
    }

    public String getPreviewContentType() {
        return previewContentType;
    }

    public Long getPreviewSizeBytes() {
        return previewSizeBytes;
    }

    public Instant getPreviewCreatedAt() {
        return previewCreatedAt;
    }
}
