package ru.ticketpoint.backend.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketpoint.backend.domain.*;
import ru.ticketpoint.backend.dto.*;
import ru.ticketpoint.backend.error.ApiException;
import ru.ticketpoint.backend.repository.*;
import ru.ticketpoint.backend.security.CurrentUser;

@Service
public class ReferenceService {
    private final Clock clock;
    private final EventRepository events;
    private final CustomerRepository customers;
    private final AuditService audit;
    private final CurrentUser current;

    public ReferenceService(
            EventRepository events,
            CustomerRepository customers,
            AuditService audit,
            CurrentUser current, Clock clock) {
        this.clock = clock;
        this.events = events;
        this.customers = customers;
        this.audit = audit;
        this.current = current;
    }

    @Transactional(readOnly = true)
    public List<Views.EventView> events() {
        return events.findAll(Sort.by("startsAt")).stream()
                .map(e -> Views.event(e, LocalDateTime.now(clock)))
                .toList();
    }

    @Transactional
    public Views.EventView save(UUID id, Requests.EventInput req) {
        var e = id == null ? new Event() : events.findByPublicId(id).orElseThrow(ApiException::missing);
        if (id != null) ApiException.version(req.version(), e.getVersion());
        e.setTitle(req.title().trim());
        e.setVenue(req.venue().trim());
        e.setStartsAt(req.startsAt());
        e.setPrice(req.price());
        e.setActive(req.active());
        events.saveAndFlush(e);
        audit.record(
                id == null ? "EVENT_CREATED" : "EVENT_UPDATED",
                "EVENT",
                e.getPublicId(),
                "Изменено мероприятие " + e.getTitle());
        return Views.event(e, LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    public List<Customer> customers() {
        var u = current.get();
        if (u.getRole().equals("USER"))
            return u.getCustomerId() == null ? List.of() : customers.findById(u.getCustomerId()).stream().toList();
        return customers.findAll(Sort.by("name"));
    }

    @Transactional
    public Customer createCustomer(Requests.CustomerInput req) {
        var c = new Customer();
        c.setCode("CUS-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
        c.setName(req.name().trim());
        customers.save(c);
        audit.record("CUSTOMER_CREATED", "CUSTOMER", null, "Создан клиент " + c.getName());
        return c;
    }
}
