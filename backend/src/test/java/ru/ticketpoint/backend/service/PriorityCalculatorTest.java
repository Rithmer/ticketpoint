package ru.ticketpoint.backend.service;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import ru.ticketpoint.backend.domain.*;
import ru.ticketpoint.backend.error.ApiException;

import static org.junit.jupiter.api.Assertions.*;

class PriorityCalculatorTest {
    @Test
    void priorityHonorsFinalStatusVipAndPriceBoundary() {
        assertEquals(
                Priority.HIGH, PriorityCalculator.calculate(BookingStatus.NEW, BigDecimal.ONE, true));
        assertEquals(
                Priority.MEDIUM,
                PriorityCalculator.calculate(BookingStatus.CONFIRMED, new BigDecimal("5000"), false));
        assertEquals(
                Priority.LOW,
                PriorityCalculator.calculate(BookingStatus.NEW, new BigDecimal("4999.99"), false));
        assertEquals(
                Priority.LOW,
                PriorityCalculator.calculate(BookingStatus.PAID, new BigDecimal("9000"), true));
        assertEquals(
                Priority.LOW, PriorityCalculator.calculate(BookingStatus.CANCELLED, BigDecimal.ONE, true));
    }

    @Test
    void rejectsInvalidLifecycleChanges() {
        assertThrows(
                ApiException.class,
                () -> BookingApplicationService.transition(BookingStatus.NEW, BookingStatus.PAID));
        assertThrows(
                ApiException.class,
                () -> BookingApplicationService.transition(BookingStatus.CANCELLED, BookingStatus.NEW));
        assertDoesNotThrow(
                () -> BookingApplicationService.transition(BookingStatus.CONFIRMED, BookingStatus.PAID));
    }
}
