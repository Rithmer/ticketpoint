package ru.ticketpoint.repository;

import ru.ticketpoint.model.CreateEventCommand;
import ru.ticketpoint.model.Event;

import java.util.List;
import java.util.Optional;

public interface EventRepository {
    Event create(CreateEventCommand event);
    Event update(long id, CreateEventCommand command);
    boolean delete(long id);
    List<Event> findAll();
    Optional<Event> findById(long id);
}
