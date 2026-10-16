package ru.ticketpoint.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CreateEventCommand(String title, String venue, LocalDateTime startsAt, BigDecimal price) {
    public CreateEventCommand {
        title = Validation.text(title, "Название мероприятия", 200);
        venue = Validation.text(venue, "Место проведения", 200);
        if (startsAt == null) throw new IllegalArgumentException("Дата мероприятия: значение обязательно");
        if (startsAt.getYear() < 1900 || startsAt.getYear() > 9999) {
            throw new IllegalArgumentException("Дата мероприятия должна быть в диапазоне 1900–9999 годов");
        }
        price = Validation.price(price);
    }
}
