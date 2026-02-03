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

    @Column(nullable = false)
    private BigDecimal originalPrice;

    @Column(nullable = false)
    private BigDecimal resalePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TicketStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TicketLot() {}

    public TicketLot(String uid, String eventName, LocalDateTime eventDate,
                     BigDecimal originalPrice, BigDecimal resalePrice, User seller) {
        this.uid = uid;
        this.eventName = eventName;
        this.eventDate = eventDate;
        this.originalPrice = originalPrice;
        this.resalePrice = resalePrice;
        this.seller = seller;
        this.status = TicketStatus.CREATED;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getUid() { return uid; }
    public String getEventName() { return eventName; }
    public LocalDateTime getEventDate() { return eventDate; }
    public BigDecimal getOriginalPrice() { return originalPrice; }
    public BigDecimal getResalePrice() { return resalePrice; }
    public TicketStatus getStatus() { return status; }
    public User getSeller() { return seller; }
    public Instant getCreatedAt() { return createdAt; }
}
