package ru.ticketpoint.backend.controller;

import jakarta.validation.Valid;

import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.ticketpoint.backend.dto.*;
import ru.ticketpoint.backend.service.NoteService;

@RestController
@RequestMapping("/api/bookings/{bookingId}/notes")
public class NoteController {
    private final NoteService notes;

    public NoteController(NoteService notes) {
        this.notes = notes;
    }

    @GetMapping
    public List<Views.NoteView> list(@PathVariable UUID bookingId) {
        return notes.list(bookingId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Views.NoteView create(
            @PathVariable UUID bookingId, @Valid @RequestBody Requests.NoteInput req) {
        return notes.save(bookingId, null, req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Views.NoteView update(
            @PathVariable UUID bookingId,
            @PathVariable UUID id,
            @Valid @RequestBody Requests.NoteInput req) {
        return notes.save(bookingId, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public void delete(@PathVariable UUID bookingId, @PathVariable UUID id) {
        notes.delete(bookingId, id);
    }
}
