package ru.ticketpoint.backend.controller;

import jakarta.validation.Valid;

import java.util.*;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.ticketpoint.backend.domain.AuditEvent;
import ru.ticketpoint.backend.dto.*;
import ru.ticketpoint.backend.filter.BookingSearch;
import ru.ticketpoint.backend.repository.AuditEventRepository;
import ru.ticketpoint.backend.service.UserAdminService;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final UserAdminService users;
    private final AuditEventRepository audit;

    public AdminController(UserAdminService users, AuditEventRepository audit) {
        this.users = users;
        this.audit = audit;
    }

    @GetMapping("/users")
    public Views.PageView<Views.UserView> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "login") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        return Views.PageView.from(
                users.list(
                        BookingSearch.page(page, size, sort, direction, Set.of("login", "name", "role"))));
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Views.UserView create(@Valid @RequestBody Requests.NewUser req) {
        return users.create(req);
    }

    @PutMapping("/users/{id}")
    public Views.UserView update(@PathVariable UUID id, @Valid @RequestBody Requests.UserInput req) {
        return users.update(id, req);
    }

    @PostMapping("/users/{id}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@PathVariable UUID id, @Valid @RequestBody Requests.ResetPassword req) {
        users.reset(id, req.temporaryPassword());
    }

    @GetMapping("/audit")
    public Views.PageView<Views.AuditView> audit(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurredAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        Specification<AuditEvent> spec =
                (root, query, cb) -> {
                    if (q == null || q.isBlank()) return cb.conjunction();
                    String pattern =
                            "%"
                                    + q.toLowerCase(Locale.ROOT)
                                    .replace("\\", "\\\\")
                                    .replace("%", "\\%")
                                    .replace("_", "\\_")
                                    + "%";
                    return cb.or(
                            cb.like(cb.lower(root.get("summary")), pattern, '\\'),
                            cb.like(cb.lower(root.get("actor")), pattern, '\\'),
                            cb.like(cb.lower(root.get("eventType")), pattern, '\\'),
                            cb.like(cb.lower(root.get("correlationId")), pattern, '\\'));
                };
        return Views.PageView.from(
                audit.findAll(
                                spec,
                                BookingSearch.page(
                                        page,
                                        size,
                                        sort,
                                        direction,
                                        Set.of("occurredAt", "actor", "eventType", "entityType")))
                        .map(Views::audit));
    }
}
