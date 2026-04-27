package ru.ticketswap.purchase;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import ru.ticketswap.ticket.TicketLot;
import ru.ticketswap.user.User;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "purchase_orders")
public class PurchaseOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    private TicketLot listing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User seller;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private PurchaseOrderStatus status = PurchaseOrderStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 40)
    private PaymentStatus paymentStatus = PaymentStatus.NOT_STARTED;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency = "RUB";

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "partner_operation_id", length = 255)
    private String partnerOperationId;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PurchaseOrder() {
    }

    public PurchaseOrder(TicketLot listing, User buyer, String idempotencyKey) {
        this.listing = listing;
        this.buyer = buyer;
        this.seller = listing.getSeller();
        this.amount = listing.getResalePrice();
        this.idempotencyKey = idempotencyKey;
        this.status = PurchaseOrderStatus.CREATED;
        this.paymentStatus = PaymentStatus.NOT_STARTED;
        this.currency = "RUB";
    }

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
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

    public User getSeller() {
        return seller;
    }

    public PurchaseOrderStatus getStatus() {
        return status;
    }

    public void setStatus(PurchaseOrderStatus status) {
        this.status = status;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(PaymentStatus paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getPartnerOperationId() {
        return partnerOperationId;
    }

    public void setPartnerOperationId(String partnerOperationId) {
        this.partnerOperationId = partnerOperationId;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
