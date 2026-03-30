package ru.ticketswap.auth;

import jakarta.persistence.*;
import ru.ticketswap.user.User;

import java.time.Instant;

@Entity
@Table(
        name = "two_factor_challenges",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_two_factor_challenges_challenge_id", columnNames = "challenge_id")
        },
        indexes = {
                @Index(name = "idx_two_factor_challenges_user_id", columnList = "user_id"),
                @Index(name = "idx_two_factor_challenges_expires_at", columnList = "expires_at")
        }
)
public class TwoFactorChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "challenge_id", nullable = false, length = 64)
    private String challengeId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TwoFactorChallenge() {
    }

    public TwoFactorChallenge(User user, String challengeId, String codeHash, Instant expiresAt) {
        this.user = user;
        this.challengeId = challengeId;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.failedAttempts = 0;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public void markConsumed(Instant now) {
        if (consumedAt == null) {
            consumedAt = now;
        }
    }

    public int incrementFailedAttempts() {
        failedAttempts += 1;
        return failedAttempts;
    }

    public User getUser() {
        return user;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }
}
