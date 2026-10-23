package ru.ticketpoint.model;

public record BookingStatistics(long total, long newBookings, long confirmed, long paid, long cancelled,
                                long expensiveTickets) {

}
