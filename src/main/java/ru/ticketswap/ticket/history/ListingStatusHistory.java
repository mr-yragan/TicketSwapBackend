package ru.ticketswap.ticket.history;

import jakarta.persistence.*;
import ru.ticketswap.ticket.TicketLot;
import ru.ticketswap.ticket.TicketStatus;
import ru.ticketswap.user.User;

import java.time.Instant;

@Entity
@Table(name = "listing_status_history")
public class ListingStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private TicketLot listing;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status")
    private TicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private TicketStatus toStatus;

    @Column(length = 255)
    private String reason;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "changed_by_user_id")
    private User changedByUser;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected ListingStatusHistory() {
    }

    public ListingStatusHistory(
            TicketLot listing,
            TicketStatus fromStatus,
            TicketStatus toStatus,
            String reason,
            User changedByUser,
            Instant changedAt
    ) {
        this.listing = listing;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.changedByUser = changedByUser;
        this.changedAt = changedAt;
    }

    public Long getId() {
        return id;
    }

    public TicketLot getListing() {
        return listing;
    }

    public TicketStatus getFromStatus() {
        return fromStatus;
    }

    public TicketStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public User getChangedByUser() {
        return changedByUser;
    }

    public Instant getChangedAt() {
        return changedAt;
    }
}
