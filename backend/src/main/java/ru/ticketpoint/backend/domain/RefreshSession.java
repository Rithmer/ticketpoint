package ru.ticketpoint.backend.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {
    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id")
    private UserAccount user;

    @Column(name = "token_hash", length = 64, nullable = false, unique = true)

    @SuppressWarnings({"unused", "FieldCanBeLocal"})
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    @SuppressWarnings("unused")
    private Instant createdAt = Instant.now();

    @Column(name = "user_version", nullable = false)
    private long userVersion;

    public UUID getId() {
        return id;
    }

    public void setId(UUID value) {
        this.id = value;
    }

    public UserAccount getUser() {
        return user;
    }

    public void setUser(UserAccount value) {
        this.user = value;
    }

    public void setTokenHash(String value) {
        this.tokenHash = value;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant value) {
        this.expiresAt = value;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant value) {
        this.revokedAt = value;
    }

    public long getUserVersion() {
        return userVersion;
    }

    public void setUserVersion(long value) {
        this.userVersion = value;
    }
}
