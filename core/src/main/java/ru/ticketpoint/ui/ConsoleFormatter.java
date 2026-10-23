package ru.ticketpoint.ui;

import java.time.format.DateTimeFormatter;

import ru.ticketpoint.model.Booking;
import ru.ticketpoint.model.Customer;
import ru.ticketpoint.model.Event;

public final class ConsoleFormatter {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm");

    private ConsoleFormatter() {
    }

    public static String format(Object item) {
        if (item instanceof Booking booking) {
            return "Бронь #%s | %s | Клиент #%s: %s [%s] | Мероприятие #%s: %s | %s | %s | %s руб. | %s"
                    .formatted(booking.id(), booking.ticketNumber(), booking.customer().id(),
                            booking.customer().name(), booking.customer().customerCode(),
                            booking.event().id(), booking.event().title(),
                            booking.event().venue(),
                            DATE.format(booking.event().startsAt()),
                            booking.priceAtBooking().toPlainString(), booking.status());
        }
        if (item instanceof Customer(Long id, String code, String name)) {
            return "Клиент #%s: %s [%s]".formatted(id, name, code);
        }
        if (item instanceof Event event) {
            return "Мероприятие #%s: %s | %s | %s | %s руб.".formatted(event.id(), event.title(),
                    event.venue(), DATE.format(event.startsAt()), event.price().toPlainString());
        }
        if (item instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("Неподдерживаемый тип для консольного вывода");
    }
}
