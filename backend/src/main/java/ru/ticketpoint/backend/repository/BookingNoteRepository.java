package ru.ticketpoint.backend.repository;

import java.util.*;

import org.springframework.data.jpa.repository.*;
import ru.ticketpoint.backend.domain.Booking;
import ru.ticketpoint.backend.domain.BookingNote;

public interface BookingNoteRepository
        extends JpaRepository<BookingNote, Long>, JpaSpecificationExecutor<BookingNote> {
    Optional<BookingNote> findByPublicId(UUID id);

    List<BookingNote> findByBookingOrderByPinnedDescCreatedAtDesc(Booking booking);

    void deleteByBooking(Booking booking);
}
