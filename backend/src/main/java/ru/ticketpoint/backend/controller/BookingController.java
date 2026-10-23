package ru.ticketpoint.backend.controller;

import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.ticketpoint.backend.domain.*;
import ru.ticketpoint.backend.dto.*;
import ru.ticketpoint.backend.filter.BookingSearch;
import ru.ticketpoint.backend.service.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {
    private final BookingApplicationService bookings;
    private final BulkService bulk;
    private final CsvService csv;

    public BookingController(BookingApplicationService bookings, BulkService bulk, CsvService csv) {
        this.bookings = bookings;
        this.bulk = bulk;
        this.csv = csv;
    }

    private static final Set<String> SORT =
            Set.of("ticketNumber", "price", "status", "createdAt", "updatedAt");

    @GetMapping
    public Views.PageView<Views.BookingView> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Boolean vip,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "false") Boolean archived,
            @RequestParam(required = false) BigDecimal price,
            @RequestParam(defaultValue = "eq") String comparison,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return Views.PageView.from(
                bookings.search(
                        new BookingSearch.Filter(
                                q, eventId, status, vip, priority, tag, archived, price, comparison),
                        BookingSearch.page(page, size, sort, direction, SORT)));
    }

    @GetMapping("/{id}")
    public Views.BookingView get(@PathVariable UUID id) {
        return bookings.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    public Views.BookingView create(@Valid @RequestBody Requests.BookingInput req) {
        return bookings.create(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    public Views.BookingView update(
            @PathVariable UUID id, @Valid @RequestBody Requests.BookingInput req) {
        return bookings.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    public Views.BookingView archive(@PathVariable UUID id) {
        return bookings.archive(id);
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Views.BookingView restore(@PathVariable UUID id) {
        return bookings.restore(id);
    }

    @DeleteMapping("/{id}/permanent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void permanent(
            @PathVariable UUID id,
            @RequestHeader(value = "X-Confirm-Delete", required = false) String confirm) {
        bookings.permanent(id, confirm);
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Views.BulkResult bulk(@Valid @RequestBody Requests.Bulk req) {
        return bulk.apply(req);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> export(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID eventId,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false) Boolean vip,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "false") Boolean archived,
            @RequestParam(required = false) BigDecimal price,
            @RequestParam(defaultValue = "eq") String comparison,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=bookings.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(
                        csv.export(
                                new BookingSearch.Filter(
                                        q, eventId, status, vip, priority, tag, archived, price, comparison),
                                BookingSearch.page(0, 20, sort, direction, SORT).getSort()));
    }

    @PostMapping(value = "/import/preview", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CsvService.Preview preview(
            @RequestParam MultipartFile file, @RequestParam(defaultValue = "NEW_ONLY") String mode) {
        return csv.preview(file, mode);
    }

    @PostMapping("/import/apply")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CsvService.ImportResult apply(@Valid @RequestBody Requests.ApplyImport req) {
        return csv.apply(req);
    }
}
