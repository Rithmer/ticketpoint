package ru.ticketpoint.service;

import ru.ticketpoint.exception.EntityNotFoundException;
import ru.ticketpoint.model.CreateEventCommand;
import ru.ticketpoint.model.Event;
import ru.ticketpoint.model.Validation;
import ru.ticketpoint.repository.EventRepository;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

public final class EventService {
    private final EventRepository events;
    private final Clock clock;

    private final AccessPolicy access;

    public EventService(EventRepository events, Clock clock) {
        this(events, clock, AccessPolicy.TRUSTED);
    }

    public EventService(EventRepository events, Clock clock, AccessPolicy access) {
        this.access = Objects.requireNonNull(access);
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
    }

    public Event create(CreateEventCommand event) {
        access.admin();
        Objects.requireNonNull(event);
        EventSchedule.requireFuture(event.startsAt(), clock);
        return events.create(event);
    }

    public Event update(long id, CreateEventCommand command) {
        access.admin(); findById(id);
        EventSchedule.requireFuture(command.startsAt(), clock);
        return events.update(id, command);
    }

    public void delete(long id) {
        access.admin();
        if (!events.delete(Validation.id(id))) throw new EntityNotFoundException("Мероприятие не найдено");
    }

    public List<Event> findAll() {
        access.read().run();
        return events.findAll();
    }

    public Event findById(long id) {
        access.read().run();
        return events.findById(Validation.id(id))
                .orElseThrow(() -> new EntityNotFoundException("Мероприятие не найдено"));
    }

}
