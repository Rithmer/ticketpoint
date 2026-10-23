package ru.ticketpoint.backend.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

import ru.ticketpoint.backend.config.LocalTimestampListener;

@Entity
@EntityListeners(LocalTimestampListener.class)
@Table(name = "booking_notes")
public class BookingNote extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "author_id")
    private UserAccount author;

    @Column(nullable = false, length = 4000)
    private String body;

    private boolean pinned;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;


    public Booking getBooking() {
        return booking;
    }

    public void setBooking(Booking value) {
        this.booking = value;
    }

    public UserAccount getAuthor() {
        return author;
    }

    public void setAuthor(UserAccount value) {
        this.author = value;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String value) {
        this.body = value;
    }

    public boolean getPinned() {
        return pinned;
    }

    public void setPinned(boolean value) {
        this.pinned = value;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime value) {
        this.createdAt = value;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime value) {
        this.updatedAt = value;
    }
}
