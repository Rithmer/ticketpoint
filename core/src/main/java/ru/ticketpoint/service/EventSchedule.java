package ru.ticketpoint.service;

import java.time.Clock;
import java.time.LocalDateTime;

import ru.ticketpoint.exception.BookingException;

final class EventSchedule {
    private EventSchedule() {
    }

    static void requireFuture(LocalDateTime startsAt, Clock clock) {
        if (!startsAt.isAfter(LocalDateTime.now(clock))) {
            throw new BookingException("Дата и время мероприятия уже прошли. Выберите будущее мероприятие");
        }
    }
}
