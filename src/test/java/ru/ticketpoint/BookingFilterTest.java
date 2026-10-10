package ru.ticketpoint;

import org.junit.jupiter.api.Test;
import ru.ticketpoint.model.*;
import ru.ticketpoint.service.BookingFilter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class BookingFilterTest {
    private final Booking booking = new Booking(1L, "TP-EXAMPLE",
            new Customer(2L, "CUS-00000000-0000-0000-0000-000000000002", "Анна Иванова"),
            new Event(3L, "Джазовый концерт", "Большой зал", LocalDateTime.of(2027, 5, 10, 19, 0), new BigDecimal("5000.00")),
            new BigDecimal("4900.00"), BookingStatus.PAID, LocalDateTime.of(2027, 1, 1, 12, 0));

    @Test void searchIsCaseInsensitiveAcrossMeaningfulFieldsAndUsesLiteralText() {
        for (String query : new String[]{"tp-example", " ИВАНОВА ", "джаз", "БОЛЬШОЙ", "000000000002"}) {
            assertTrue(new BookingFilter(query, null, null, null, null, null).matches(booking), query);
        }
        assertFalse(new BookingFilter("%", null, null, null, null, null).matches(booking));
        assertFalse(new BookingFilter("нет совпадений", null, null, null, null, null).matches(booking));
    }

    @Test void filtersCombineAndDateBoundsIncludeWholeEventDay() {
        LocalDate day = booking.event().startsAt().toLocalDate();
        assertTrue(new BookingFilter("концерт", BookingStatus.PAID, 2L, 3L, day, day).matches(booking));
        assertFalse(new BookingFilter("", BookingStatus.NEW, 2L, 3L, day, day).matches(booking));
        assertFalse(new BookingFilter("", null, 8L, null, null, null).matches(booking));
        assertFalse(new BookingFilter("", null, null, 8L, null, null).matches(booking));
        assertFalse(new BookingFilter("", null, null, null, day.plusDays(1), null).matches(booking));
        assertFalse(new BookingFilter("", null, null, null, null, day.minusDays(1)).matches(booking));
    }

    @Test void emptyFiltersShowEverythingAndInvertedPeriodIsRejected() {
        assertTrue(new BookingFilter(null, null, null, null, null, null).matches(booking));
        assertThrows(IllegalArgumentException.class, () -> new BookingFilter("", null, null, null,
                LocalDate.of(2027, 5, 11), LocalDate.of(2027, 5, 10)));
    }
}
