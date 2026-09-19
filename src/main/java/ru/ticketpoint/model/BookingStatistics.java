package ru.ticketpoint.model;

public record BookingStatistics(long total, long newBookings, long paid, long cancelled,
                                long expensiveTickets) { }
