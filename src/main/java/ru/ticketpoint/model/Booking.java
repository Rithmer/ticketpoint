package ru.ticketpoint.model;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Objects;

public final class Booking {
    private final Long id;
    private final String ticketNumber;
    private final Customer customer;
    private final Event event;
    private final BigDecimal priceAtBooking;
    private BookingStatus status;
    private final LocalDateTime createdAt;

    public Booking(Long id, String ticketNumber, Customer customer, Event event,
                   BigDecimal priceAtBooking, BookingStatus status, LocalDateTime createdAt) {
        this.id = Validation.optionalId(id);
        this.ticketNumber = Validation.text(ticketNumber, "Номер билета", 40);
        this.customer = Objects.requireNonNull(customer);
        this.event = Objects.requireNonNull(event);
        this.priceAtBooking = Validation.price(priceAtBooking);
        this.status = Objects.requireNonNull(status);
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public Long getId() { return id; }
    public String getTicketNumber() { return ticketNumber; }
    public Customer getCustomer() { return customer; }
    public Event getEvent() { return event; }
    public BigDecimal getPriceAtBooking() { return priceAtBooking; }
    public BookingStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void changeStatus(BookingStatus newStatus) {
        status.validateTransitionTo(newStatus);
        status = newStatus;
    }

    @Override
    public String toString() {
        return "Booking{id=%s, ticket='%s', customer='%s', event='%s', date=%s, status=%s}"
                .formatted(id, ticketNumber, customer.getName(), event.getTitle(), event.getStartsAt(), status);
    }
}
