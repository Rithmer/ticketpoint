package ru.ticketpoint.backend.controller;

import jakarta.validation.Valid;

import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import ru.ticketpoint.backend.dto.*;
import ru.ticketpoint.backend.service.ReferenceService;

@RestController
@RequestMapping("/api")
public class ReferenceController {
    private final ReferenceService references;

    public ReferenceController(ReferenceService references) {
        this.references = references;
    }

    @GetMapping("/events")
    public List<Views.EventView> events() {
        return references.events();
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Views.EventView create(@Valid @RequestBody Requests.EventInput req) {
        return references.save(null, req);
    }

    @PutMapping("/events/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Views.EventView update(
            @PathVariable UUID id, @Valid @RequestBody Requests.EventInput req) {
        return references.save(id, req);
    }

    @GetMapping("/customers")
    public List<Views.CustomerView> customers() {
        return references.customers().stream().map(Views::customer).toList();
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public Views.CustomerView createCustomer(@Valid @RequestBody Requests.CustomerInput req) {
        return Views.customer(references.createCustomer(req));
    }
}
