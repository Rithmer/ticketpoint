package ru.ticketpoint.backend.service;

import java.math.BigDecimal;

import ru.ticketpoint.backend.domain.*;

public final class PriorityCalculator {
    private PriorityCalculator() {
    }

    public static Priority calculate(BookingStatus status, BigDecimal price, boolean vip) {
        if (status == BookingStatus.CANCELLED || status == BookingStatus.PAID) return Priority.LOW;
        if (vip) return Priority.HIGH;
        return price.compareTo(new BigDecimal("5000")) >= 0 ? Priority.MEDIUM : Priority.LOW;
    }
}
