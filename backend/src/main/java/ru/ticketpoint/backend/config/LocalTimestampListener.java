package ru.ticketpoint.backend.config;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

import java.time.Clock;
import java.time.LocalDateTime;

import ru.ticketpoint.backend.domain.Booking;
import ru.ticketpoint.backend.domain.BookingNote;

public class LocalTimestampListener {
    private final Clock clock;

    public LocalTimestampListener(Clock clock) {
        this.clock = clock;
    }

    @PrePersist
    public void created(Object entity) {
        var now = LocalDateTime.now(clock);
        if (entity instanceof Booking b) {
            b.setCreatedAt(now);
            b.setUpdatedAt(now);
        }
        if (entity instanceof BookingNote n) {
            n.setCreatedAt(now);
            n.setUpdatedAt(now);
        }
    }

    @PreUpdate
    public void updated(Object entity) {
        var now = LocalDateTime.now(clock);
        if (entity instanceof Booking b) b.setUpdatedAt(now);
        if (entity instanceof BookingNote n) n.setUpdatedAt(now);
    }
}
