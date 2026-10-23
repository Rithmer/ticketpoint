package ru.ticketpoint.backend.repository;

import java.util.*;

import org.springframework.data.jpa.repository.*;
import ru.ticketpoint.backend.domain.Booking;

public interface BookingRepository
        extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {
    Optional<Booking> findByPublicId(UUID id);

    Optional<Booking> findByTicketNumberIgnoreCase(String code);
}
