package ru.ticketpoint.backend.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "actor_id")
    private UUID actorId;

    private String actor;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(nullable = false, length = 1000)
    private String summary;

    @Column(name = "correlation_id", nullable = false, length = 100)
    private String correlationId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public AuditEvent() {
        occurredAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID value) {
        this.id = value;
    }

    public UUID getActorId() {
        return actorId;
    }

    public void setActorId(UUID value) {
        this.actorId = value;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String value) {
        this.actor = value;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String value) {
        this.eventType = value;
    }

    public String getEntityType() {
        return entityType;
    }

    public void setEntityType(String value) {
        this.entityType = value;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public void setEntityId(UUID value) {
        this.entityId = value;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String value) {
        this.summary = value;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String value) {
        this.correlationId = value;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
