package ru.ticketpoint;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.model.Booking;
import ru.ticketpoint.model.BookingStatus;
import ru.ticketpoint.model.CreateEventCommand;
import ru.ticketpoint.model.Customer;
import ru.ticketpoint.model.Event;

import static org.junit.jupiter.api.Assertions.*;

class ModelTest {
    private static final String CUSTOMER_CODE = "CUS-12345678-1234-1234-1234-1234567890AB";

    @ParameterizedTest
    @ValueSource(strings = {"-1", "1.001", "10000000000"})
    void rejectsPricesThatCannotBeStoredExactly(String value) {
        assertThrows(IllegalArgumentException.class, () -> event(value));
    }

    @Test
    void keepsValidPriceAndAllowsReadingHistoricalEvent() {
        Event event = event("12.340");
        assertEquals(new BigDecimal("12.34"), event.price());
        assertTrue(event.startsAt().isBefore(LocalDateTime.now()));
    }

    @Test
    void enforcesStringLengthsAndNonblankValues() {
        assertThrows(IllegalArgumentException.class, () -> new Customer(null, CUSTOMER_CODE, " "));
        assertThrows(IllegalArgumentException.class, () -> new Customer(null, CUSTOMER_CODE, "a".repeat(121)));
        assertThrows(IllegalArgumentException.class, () -> new Customer(null, "bad-code", "Name"));
        assertThrows(IllegalArgumentException.class, () -> new Event(null, "a".repeat(201), "Hall",
                LocalDateTime.now(), BigDecimal.ZERO));
    }

    @Test
    void normalizesCustomerCodeIndependentlyOfSystemLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(CUSTOMER_CODE, new Customer(null, " " + CUSTOMER_CODE.toLowerCase() + " ", "Иван")
                    .customerCode());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void bookingRequiresValidSnapshotPrice() {
        Customer customer = new Customer(1L, CUSTOMER_CODE, "Иван");
        Event event = event("100.00");
        assertThrows(IllegalArgumentException.class, () -> new Booking(1L, "TP-TEST", customer, event,
                null, BookingStatus.NEW, LocalDateTime.now()));
        assertThrows(IllegalArgumentException.class, () -> new Booking(1L, "TP-TEST", customer, event,
                new BigDecimal("-1"), BookingStatus.NEW, LocalDateTime.now()));
    }

    @Test
    void validatesEveryStatusTransition() {
        for (BookingStatus from : BookingStatus.values()) {
            for (BookingStatus to : BookingStatus.values()) {
                boolean allowed = from != BookingStatus.CANCELLED
                        && !(from == BookingStatus.NEW && to == BookingStatus.PAID)
                        && !(from == BookingStatus.PAID && to != BookingStatus.PAID && to != BookingStatus.CANCELLED);
                assertEquals(allowed, from.canTransitionTo(to));
                assertEquals(allowed, from.allowedTransitions().contains(to));
                if (!allowed) {
                    assertThrows(BookingException.class, () -> from.validateTransitionTo(to));
                } else {
                    assertDoesNotThrow(() -> from.validateTransitionTo(to));
                }
            }
            assertFalse(from.canTransitionTo(null));
            assertThrows(BookingException.class, () -> from.validateTransitionTo(null));
        }
    }

    @Test
    void recordValuesCompareAfterNormalization() {
        Customer first = new Customer(1L, CUSTOMER_CODE.toLowerCase(Locale.ROOT), " Иван ");
        Customer second = new Customer(1L, CUSTOMER_CODE, "Иван");
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(new Customer(2L, CUSTOMER_CODE, "Иван"), first);
        assertEquals(event("12.340"), event("12.34"));
    }

    @Test
    void eventCommandRejectsMissingAndUnsupportedDates() {
        assertThrows(IllegalArgumentException.class, () -> new CreateEventCommand("Концерт", "Зал", null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new CreateEventCommand("Концерт", "Зал", LocalDateTime.of(10000, 1, 1, 12, 0), BigDecimal.ONE));
    }

    private Event event(String price) {
        return new Event(null, "Концерт", "Зал", LocalDateTime.of(2020, 1, 1, 19, 0), new BigDecimal(price));
    }
}
