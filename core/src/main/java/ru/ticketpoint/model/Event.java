package ru.ticketpoint.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public record Event(Long id, String title, String venue, LocalDateTime startsAt, BigDecimal price) {
    public Event {
        id = Validation.optionalId(id);
        title = Validation.text(title, "Название мероприятия", 200);
        venue = Validation.text(venue, "Место проведения", 200);
        Objects.requireNonNull(startsAt, "Дата мероприятия обязательна");
        price = Validation.price(price);
    }

}
