package ru.ticketpoint.backend.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import org.junit.jupiter.api.Test;
import ru.ticketpoint.backend.config.LocalTimestampListener;
import ru.ticketpoint.backend.domain.*;
import ru.ticketpoint.backend.dto.Requests;
import ru.ticketpoint.backend.error.ApiException;
import ru.ticketpoint.backend.repository.*;
import ru.ticketpoint.backend.security.CurrentUser;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessTimeTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-22T17:00:00Z"), ZoneId.of("Europe/Moscow"));

    @Test
    void pastMoscowEventIsRejectedEvenWhenItIsStillFutureInUtc() {
        var events = mock(EventRepository.class);
        var customers = mock(CustomerRepository.class);
        var current = mock(CurrentUser.class);
        var user = new UserAccount();
        user.setRole("MANAGER");
        when(current.get()).thenReturn(user);
        var event = new Event();
        event.setId(1L);
        event.setStartsAt(LocalDateTime.parse("2026-10-22T19:00:00"));
        event.setPrice(BigDecimal.TEN);
        when(events.findByPublicId(event.getPublicId())).thenReturn(Optional.of(event));
        var customer = new Customer();
        customer.setId(1L);
        when(customers.findById(1L)).thenReturn(Optional.of(customer));
        var service = new BookingApplicationService(mock(BookingRepository.class), events, customers,
                mock(BookingNoteRepository.class), mock(AuditService.class), current, clock);
        assertThrows(ApiException.class, () -> service.create(new Requests.BookingInput(null, 1L,
                event.getPublicId(), BookingStatus.NEW, false, "", Set.of(), null)));
    }

    @Test
    void bookingAndNoteTimestampsUseBusinessZone() {
        var listener = new LocalTimestampListener(clock);
        var booking = new Booking();
        var note = new BookingNote();
        listener.created(booking);
        listener.created(note);
        assertEquals(LocalDateTime.parse("2026-10-22T20:00:00"), booking.getCreatedAt());
        assertEquals(booking.getCreatedAt(), note.getCreatedAt());
        var later = new LocalTimestampListener(Clock.offset(clock, Duration.ofMinutes(10)));
        later.updated(booking);
        later.updated(note);
        assertEquals(LocalDateTime.parse("2026-10-22T20:10:00"), booking.getUpdatedAt());
        assertEquals(booking.getUpdatedAt(), note.getUpdatedAt());
        assertEquals(LocalDateTime.parse("2026-10-22T20:00:00"), booking.getCreatedAt());
    }
}
