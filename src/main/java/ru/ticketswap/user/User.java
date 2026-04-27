package ru.ticketswap.user;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "email"),
                @UniqueConstraint(columnNames = "login")
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(unique = true, length = 32)
    private String login;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(nullable = false, length = 50)
    private String role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "two_factor_enabled", nullable = false)
    private boolean twoFactorEnabled;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    protected User() {
    }

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = "USER";
        this.emailVerified = false;
        this.tokenVersion = 0;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        if (this.role == null) {
            this.role = "USER";
        }
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isTwoFactorEnabled() {
        return twoFactorEnabled;
    }

    public void setTwoFactorEnabled(boolean twoFactorEnabled) {
        this.twoFactorEnabled = twoFactorEnabled;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(int tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public void incrementTokenVersion() {
        this.tokenVersion++;
    }
}
