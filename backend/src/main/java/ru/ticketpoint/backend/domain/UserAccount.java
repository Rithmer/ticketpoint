package ru.ticketpoint.backend.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_users")
public class UserAccount extends BaseEntity {
    @Column(nullable = false, length = 40)
    private String login;

    @Column(nullable = false, length = 120)
    private String name;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 256)
    private String passwordHash;

    @Column(nullable = false, length = 16)
    private String role = "USER";

    private boolean enabled = true;

    @Column(name = "password_change_required")
    private boolean passwordChangeRequired = true;

    @Column(name = "failed_attempts")
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "customer_id")
    private Long customerId;

    public String getLogin() {
        return login;
    }

    public void setLogin(String value) {
        this.login = value;
    }

    public String getName() {
        return name;
    }

    public void setName(String value) {
        this.name = value;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String value) {
        this.passwordHash = value;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String value) {
        this.role = value;
    }

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean value) {
        this.enabled = value;
    }

    public boolean getPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public void setPasswordChangeRequired(boolean value) {
        this.passwordChangeRequired = value;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(int value) {
        this.failedAttempts = value;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant value) {
        this.lockedUntil = value;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long value) {
        this.customerId = value;
    }
}
