package ru.ticketpoint.backend.service;

import java.util.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketpoint.backend.domain.*;
import ru.ticketpoint.backend.dto.*;
import ru.ticketpoint.backend.error.ApiException;
import ru.ticketpoint.backend.repository.*;
import ru.ticketpoint.backend.security.CurrentUser;

@Service
public class NoteService {
    private final BookingNoteRepository notes;
    private final BookingApplicationService bookings;
    private final CurrentUser current;
    private final AuditService audit;

    public NoteService(
            BookingNoteRepository notes,
            BookingApplicationService bookings,
            CurrentUser current,
            AuditService audit) {
        this.notes = notes;
        this.bookings = bookings;
        this.current = current;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Views.NoteView> list(UUID bookingId) {
        return notes.findByBookingOrderByPinnedDescCreatedAtDesc(bookings.require(bookingId)).stream()
                .map(Views::note)
                .toList();
    }

    @Transactional
    public Views.NoteView save(UUID bookingId, UUID noteId, Requests.NoteInput req) {
        var b = bookings.require(bookingId);
        var n = noteId == null ? new BookingNote() : require(bookingId, noteId);
        if (noteId == null) {
            n.setBooking(b);
            n.setAuthor(current.get());
        } else ApiException.version(req.version(), n.getVersion());
        n.setBody(req.body().trim());
        n.setPinned(req.pinned());
        notes.saveAndFlush(n);
        audit.record("NOTE_SAVED", "BOOKING", bookingId, "Заметка сохранена");
        return Views.note(n);
    }

    @Transactional
    public void delete(UUID bookingId, UUID noteId) {
        notes.delete(require(bookingId, noteId));
        audit.record("NOTE_DELETED", "BOOKING", bookingId, "Заметка удалена");
    }

    private BookingNote require(UUID bid, UUID nid) {
        var n = notes.findByPublicId(nid).orElseThrow(ApiException::missing);
        if (!n.getBooking().getPublicId().equals(bid)) throw ApiException.missing();
        return n;
    }
}
