package ru.ticketpoint.backend.repository;

import java.util.*;

import org.springframework.data.jpa.repository.*;
import ru.ticketpoint.backend.domain.Event;

public interface EventRepository
        extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {
    Optional<Event> findByPublicId(UUID id);
}
