package ru.ticketpoint.service;

import java.time.LocalDate;
import java.util.Locale;

import ru.ticketpoint.model.Booking;
import ru.ticketpoint.model.BookingStatus;

public record BookingFilter(String query, BookingStatus status, Long customerId, Long eventId,
                            LocalDate from, LocalDate to) {
    public BookingFilter {
        query = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Начало периода должно быть не позже конца периода");
        }
    }

    public boolean matches(Booking booking) {
        LocalDate day = booking.event().startsAt().toLocalDate();
        return (status == null || booking.status() == status)
                && (customerId == null || customerId.equals(booking.customer().id()))
                && (eventId == null || eventId.equals(booking.event().id()))
                && (from == null || !day.isBefore(from))
                && (to == null || !day.isAfter(to))
                && matchesQuery(booking);
    }

    private boolean matchesQuery(Booking booking) {
        return query.isEmpty()
                || contains(booking.ticketNumber())
                || contains(booking.customer().name())
                || contains(booking.customer().customerCode())
                || contains(booking.event().title())
                || contains(booking.event().venue());
    }

    private boolean contains(String text) {
        return text.toLowerCase(Locale.ROOT).contains(query);
    }
}
