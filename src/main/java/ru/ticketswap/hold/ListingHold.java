package ru.ticketswap.hold;

import jakarta.persistence.*;
import ru.ticketswap.ticket.TicketLot;
import ru.ticketswap.user.User;

import java.time.Instant;

@Entity
@Table(name = "listing_holds")
public class ListingHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private TicketLot listing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Column(name = "hold_until", nullable = false)
    private Instant holdUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ListingHold() {
    }

    public ListingHold(TicketLot listing, User buyer, Instant holdUntil) {
        this.listing = listing;
        this.buyer = buyer;
        this.holdUntil = holdUntil;
        this.createdAt = Instant.now();
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public TicketLot getListing() {
        return listing;
    }

    public User getBuyer() {
        return buyer;
    }

    public Instant getHoldUntil() {
        return holdUntil;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
