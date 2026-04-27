package ru.ticketswap.ticket;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import ru.ticketswap.event.Event;
import ru.ticketswap.organizer.Organizer;
import ru.ticketswap.user.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Entity
@Table(name = "tickets")
public class TicketLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String uid;

    @Column(name = "reissued_ticket_uid")
    private String reissuedTicketUid;

    @Column(name = "reissued_file_object_key", length = 1024)
    private String reissuedFileObjectKey;

    @Column(name = "reissued_file_original_name", length = 255)
    private String reissuedFileOriginalName;

    @Column(name = "reissued_file_content_type", length = 255)
    private String reissuedFileContentType;

    @Column(name = "reissued_file_size_bytes")
    private Long reissuedFileSizeBytes;

    @Column(name = "reissued_file_uploaded_at")
    private Instant reissuedFileUploadedAt;

    @Column(nullable = false)
    private String eventName;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "event_id")
    private Event event;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "organizer_id")
    private Organizer organizer;

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

    @OneToMany(mappedBy = "ticket", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("createdAt ASC, id ASC")
    private List<TicketFile> ticketFiles = new ArrayList<>();

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

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String getReissuedTicketUid() {
        return reissuedTicketUid;
    }

    public void setReissuedTicketUid(String reissuedTicketUid) {
        this.reissuedTicketUid = reissuedTicketUid;
    }

    public String getReissuedFileObjectKey() {
        return reissuedFileObjectKey;
    }

    public void setReissuedFileObjectKey(String reissuedFileObjectKey) {
        this.reissuedFileObjectKey = reissuedFileObjectKey;
    }

    public String getReissuedFileOriginalName() {
        return reissuedFileOriginalName;
    }

    public void setReissuedFileOriginalName(String reissuedFileOriginalName) {
        this.reissuedFileOriginalName = reissuedFileOriginalName;
    }

    public String getReissuedFileContentType() {
        return reissuedFileContentType;
    }

    public void setReissuedFileContentType(String reissuedFileContentType) {
        this.reissuedFileContentType = reissuedFileContentType;
    }

    public Long getReissuedFileSizeBytes() {
        return reissuedFileSizeBytes;
    }

    public void setReissuedFileSizeBytes(Long reissuedFileSizeBytes) {
        this.reissuedFileSizeBytes = reissuedFileSizeBytes;
    }

    public Instant getReissuedFileUploadedAt() {
        return reissuedFileUploadedAt;
    }

    public void setReissuedFileUploadedAt(Instant reissuedFileUploadedAt) {
        this.reissuedFileUploadedAt = reissuedFileUploadedAt;
    }

    public boolean hasReissuedTicketFile() {
        return reissuedFileObjectKey != null && !reissuedFileObjectKey.isBlank();
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public LocalDateTime getEventDate() {
        return eventDate;
    }

    public void setEventDate(LocalDateTime eventDate) {
        this.eventDate = eventDate;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public Organizer getOrganizer() {
        return organizer;
    }

    public void setOrganizer(Organizer organizer) {
        this.organizer = organizer;
    }

    public String getVenueName() {
        return venueName;
    }

    public void setVenueName(String venueName) {
        this.venueName = venueName;
    }

    public String getVenueCity() {
        return venueCity;
    }

    public void setVenueCity(String venueCity) {
        this.venueCity = venueCity;
    }

    public String getAdditionalInfo() {
        return additionalInfo;
    }

    public void setAdditionalInfo(String additionalInfo) {
        this.additionalInfo = additionalInfo;
    }

    public String getOrganizerName() {
        return organizerName;
    }

    public void setOrganizerName(String organizerName) {
        this.organizerName = organizerName;
    }

    public String getSellerComment() {
        return sellerComment;
    }

    public void setSellerComment(String sellerComment) {
        this.sellerComment = sellerComment;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
    }

    public BigDecimal getResalePrice() {
        return resalePrice;
    }

    public void setResalePrice(BigDecimal resalePrice) {
        this.resalePrice = resalePrice;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
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

    public List<TicketFile> getTicketFiles() {
        return Collections.unmodifiableList(ticketFiles);
    }

    public boolean hasTicketFile() {
        return !ticketFiles.isEmpty();
    }

    public int getTicketFilesCount() {
        return ticketFiles.size();
    }

    public TicketFile getOnlyTicketFileOrNull() {
        if (ticketFiles.size() != 1) {
            return null;
        }
        return ticketFiles.get(0);
    }

    public void addTicketFile(TicketFile ticketFile) {
        this.ticketFiles.add(ticketFile);
    }

    public void removeTicketFile(TicketFile ticketFile) {
        this.ticketFiles.remove(ticketFile);
    }

    public void clearTicketFiles() {
        this.ticketFiles.clear();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
