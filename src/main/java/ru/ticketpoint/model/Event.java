package ru.ticketpoint.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public final class Event {
    private final Long id;
    private final String title;
    private final String venue;
    private final LocalDateTime startsAt;
    private final BigDecimal price;

    public Event(Long id, String title, String venue, LocalDateTime startsAt, BigDecimal price) {
        this.id = Validation.optionalId(id);
        this.title = Validation.text(title, "Название мероприятия", 200);
        this.venue = Validation.text(venue, "Место проведения", 200);
        this.startsAt = Objects.requireNonNull(startsAt, "Дата мероприятия обязательна");
        this.price = Validation.price(price);
    }

    public Long getId() { return id; }

    public String getTitle() { return title; }
    public String getVenue() { return venue; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public BigDecimal getPrice() { return price; }

    @Override
    public String toString() {
        return "%s: %s; %s; %s; %s руб.".formatted(id, title, venue, startsAt, price);
    }
}
