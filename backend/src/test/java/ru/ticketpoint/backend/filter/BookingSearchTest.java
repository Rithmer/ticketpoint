package ru.ticketpoint.backend.filter;

import java.util.Set;

import org.junit.jupiter.api.Test;
import ru.ticketpoint.backend.error.ApiException;

import static org.junit.jupiter.api.Assertions.*;

class BookingSearchTest {
    @Test
    void paginationRejectsUnboundedRequestsAndUntrustedSort() {
        var fields = Set.of("price", "createdAt", "ticketNumber");
        assertEquals(100, BookingSearch.page(0, 100, "price", "asc", fields).getPageSize());
        assertThrows(
                ApiException.class,
                () -> BookingSearch.page(0, 101, "price", "asc", fields));
        assertThrows(
                ApiException.class,
                () -> BookingSearch.page(-1, 10, "price", "asc", fields));
        assertThrows(
                ApiException.class,
                () -> BookingSearch.page(0, 20, "price desc; drop table bookings", "asc", fields));
        assertThrows(
                ApiException.class,
                () -> BookingSearch.page(0, 20, "price", "oops", fields));
    }
}
