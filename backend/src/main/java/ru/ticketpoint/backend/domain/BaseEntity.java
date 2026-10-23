package ru.ticketpoint.backend.domain;

import jakarta.persistence.*;

import java.util.UUID;

@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    protected BaseEntity() {
        publicId = UUID.randomUUID();
    }

    @Version
    private long version;

    public Long getId() {
        return id;
    }

    public void setId(Long value) {
        this.id = value;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public long getVersion() {
        return version;
    }
}
