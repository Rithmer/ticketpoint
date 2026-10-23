package ru.ticketpoint.backend.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "events")
public class Event extends BaseEntity {
    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 200)
    private String venue;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    private boolean active = true;

    public String getTitle() {
        return title;
    }

    public void setTitle(String value) {
        this.title = value;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String value) {
        this.venue = value;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(LocalDateTime value) {
        this.startsAt = value;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal value) {
        this.price = value;
    }

    public boolean getActive() {
        return active;
    }

    public void setActive(boolean value) {
        this.active = value;
    }
}
