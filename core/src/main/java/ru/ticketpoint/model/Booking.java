package ru.ticketpoint.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public record Booking(Long id, String ticketNumber, Customer customer, Event event,
                      BigDecimal priceAtBooking, BookingStatus status, LocalDateTime createdAt, long version) {
    public Booking(Long id, String ticketNumber, Customer customer, Event event,
                   BigDecimal priceAtBooking, BookingStatus status, LocalDateTime createdAt) {
        this(id, ticketNumber, customer, event, priceAtBooking, status, createdAt, 0);
    }

    public Booking {
        id = Validation.optionalId(id);
        ticketNumber = Validation.text(ticketNumber, "Номер билета", 40);
        Objects.requireNonNull(customer);
        Objects.requireNonNull(event);
        priceAtBooking = Validation.price(priceAtBooking);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
    }

    public void requireUnchanged(Booking expected) {
        if (version != expected.version() || status != expected.status()
                || !customer.id().equals(expected.customer().id())
                || !event.id().equals(expected.event().id())
                || priceAtBooking.compareTo(expected.priceAtBooking()) != 0) {
            throw new ru.ticketpoint.exception.BookingException("Бронирование уже изменено другим пользователем. "
                    + "Закройте форму, обновите список и повторите изменение");
        }
    }
}
