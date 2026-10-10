package ru.ticketpoint.model;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Objects;

public record Booking(Long id, String ticketNumber, Customer customer, Event event,
                      BigDecimal priceAtBooking, BookingStatus status, LocalDateTime createdAt) {
    public Booking {
        id = Validation.optionalId(id);
        ticketNumber = Validation.text(ticketNumber, "Номер билета", 40);
        Objects.requireNonNull(customer);
        Objects.requireNonNull(event);
        priceAtBooking = Validation.price(priceAtBooking);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
    }

}
