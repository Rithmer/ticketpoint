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

    public EventService(EventRepository events, Clock clock) {
        this.events = Objects.requireNonNull(events);
        this.clock = Objects.requireNonNull(clock);
    }

    public Event create(CreateEventCommand event) {
        Objects.requireNonNull(event);
        EventSchedule.requireFuture(event.startsAt(), clock);
        return events.create(event);
    }

    public List<Event> findAll() {
        return events.findAll();
    }

    public Event findById(long id) {
        return events.findById(Validation.id(id))
                .orElseThrow(() -> new EntityNotFoundException("Мероприятие не найдено"));
    }

}
