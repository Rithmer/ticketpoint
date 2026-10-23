package ru.ticketpoint.backend.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import ru.ticketpoint.backend.config.LocalTimestampListener;

@Entity
@EntityListeners(LocalTimestampListener.class)
@Table(name = "bookings")
public class Booking extends BaseEntity {
    @Column(name = "ticket_number", nullable = false, length = 40)
    private String ticketNumber;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(name = "price_at_booking", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(columnDefinition = "booking_status", nullable = false)
    private BookingStatus status = BookingStatus.NEW;

    private boolean vip;
    private boolean archived;

    @Column(length = 1000)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @ElementCollection
    @CollectionTable(name = "booking_tags", joinColumns = @JoinColumn(name = "booking_id"))
    @Column(name = "tag", length = 50)
    private Set<String> tags = new LinkedHashSet<>();


    public String getTicketNumber() {
        return ticketNumber;
    }

    public void setTicketNumber(String value) {
        this.ticketNumber = value;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer value) {
        this.customer = value;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event value) {
        this.event = value;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal value) {
        this.price = value;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public void setStatus(BookingStatus value) {
        this.status = value;
    }

    public boolean getVip() {
        return vip;
    }

    public void setVip(boolean value) {
        this.vip = value;
    }

    public boolean getArchived() {
        return archived;
    }

    public void setArchived(boolean value) {
        this.archived = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        this.description = value;
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

    public void addTag(String tag) {
        tags.add(tag);
    }

    public void removeTag(String tag) {
        tags.remove(tag);
    }

    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    public void setTags(Set<String> value) {
        this.tags = new LinkedHashSet<>(value);
    }
}
