package ru.ticketswap.ticket;

import jakarta.persistence.*;
import ru.ticketswap.user.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "tickets")
public class TicketLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String uid;

    @Column(nullable = false)
    private String eventName;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    @Column(name = "venue_name", nullable = false)
    private String venueName;

    @Column(name = "venue_city", nullable = false)
    private String venueCity;

    @Column(name = "additional_info", length = 2000)
    private String additionalInfo;

    @Column(name = "organizer_name")
    private String organizerName;

    @Column(name = "seller_comment", length = 2000)
    private String sellerComment;

    @Column(name = "ticket_file_object_key")
    private String ticketFileObjectKey;

    @Column(name = "ticket_file_original_name")
    private String ticketFileOriginalName;

    @Column(name = "ticket_file_content_type", length = 100)
    private String ticketFileContentType;

    @Column(name = "ticket_file_size_bytes")
    private Long ticketFileSizeBytes;

    @Column(nullable = false)
    private BigDecimal originalPrice;

    @Column(nullable = false)
    private BigDecimal resalePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "buyer_id")
    private User buyer;


    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TicketLot() {
    }

    public TicketLot(
            String uid,
            String eventName,
            LocalDateTime eventDate,
            String venueName,
            String venueCity,
            BigDecimal price,
            String additionalInfo,
            String organizerName,
            String sellerComment,
            User seller
    ) {
        this.uid = uid;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.venueName = venueName;
        this.venueCity = venueCity;
        this.additionalInfo = additionalInfo;
        this.organizerName = organizerName;
        this.sellerComment = sellerComment;
        this.originalPrice = price;
        this.resalePrice = price;
        this.seller = seller;
        this.status = TicketStatus.CREATED;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUid() {
        return uid;
    }

    public String getEventName() {
        return eventName;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public String getVenueName() {
        return venueName;
    }

    public String getVenueCity() {
        return venueCity;
    }

    public String getAdditionalInfo() {
        return additionalInfo;
    }

    public String getOrganizerName() {
        return organizerName;
    }

    public String getSellerComment() {
        return sellerComment;
    }

    public String getTicketFileObjectKey() {
        return ticketFileObjectKey;
    }

    public String getTicketFileOriginalName() {
        return ticketFileOriginalName;
    }

    public String getTicketFileContentType() {
        return ticketFileContentType;
    }

    public Long getTicketFileSizeBytes() {
        return ticketFileSizeBytes;
    }

    public boolean hasTicketFile() {
        return ticketFileObjectKey != null && !ticketFileObjectKey.isBlank();
    }

    public void updateTicketFile(String objectKey, String originalName, String contentType, long sizeBytes) {
        this.ticketFileObjectKey = objectKey;
        this.ticketFileOriginalName = originalName;
        this.ticketFileContentType = contentType;
        this.ticketFileSizeBytes = sizeBytes;
    }

    public void clearTicketFile() {
        this.ticketFileObjectKey = null;
        this.ticketFileOriginalName = null;
        this.ticketFileContentType = null;
        this.ticketFileSizeBytes = null;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public BigDecimal getResalePrice() {
        return resalePrice;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public User getSeller() {
        return seller;
    }

    public User getBuyer() {
        return buyer;
    }

    public void setBuyer(User buyer) {
        this.buyer = buyer;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
