package ru.ticketpoint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.ticketpoint.exception.BookingException;
import ru.ticketpoint.model.BookingStatus;
import ru.ticketpoint.model.Booking;
import ru.ticketpoint.model.Customer;
import ru.ticketpoint.model.Event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

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
        assertEquals(new BigDecimal("12.34"), event.getPrice());
        assertTrue(event.getStartsAt().isBefore(LocalDateTime.now()));
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
                    .getCustomerCode());
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
                if (from == BookingStatus.CANCELLED || from == BookingStatus.PAID && to == BookingStatus.NEW) {
                    assertThrows(BookingException.class, () -> from.validateTransitionTo(to));
                } else {
                    assertDoesNotThrow(() -> from.validateTransitionTo(to));
                }
            }
            assertThrows(BookingException.class, () -> from.validateTransitionTo(null));
        }
    }

    private Event event(String price) {
        return new Event(null, "Концерт", "Зал", LocalDateTime.of(2020, 1, 1, 19, 0), new BigDecimal(price));
    }
}
