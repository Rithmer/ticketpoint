package ru.ticketpoint.model;

public record BookingStatistics(long total, long newBookings, long confirmed, long paid, long cancelled,
                                long expensiveTickets) {
    public BookingStatistics(long total, long newBookings, long paid, long cancelled, long expensiveTickets) {
        this(total, newBookings, 0, paid, cancelled, expensiveTickets);
    }
}
