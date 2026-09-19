package ru.ticketpoint.ui;

import ru.ticketpoint.model.Booking;
import ru.ticketpoint.model.Customer;
import ru.ticketpoint.model.Event;

import java.time.format.DateTimeFormatter;

public final class ConsoleFormatter {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm");

    private ConsoleFormatter() { }

    public static String format(Object item) {
        if (item instanceof Booking booking) {
            return "Бронь #%s | %s | Клиент #%s: %s [%s] | Мероприятие #%s: %s | %s | %s | %s руб. | %s"
                    .formatted(booking.getId(), booking.getTicketNumber(), booking.getCustomer().getId(),
                            booking.getCustomer().getName(), booking.getCustomer().getCustomerCode(),
                            booking.getEvent().getId(), booking.getEvent().getTitle(),
                            booking.getEvent().getVenue(),
                            DATE.format(booking.getEvent().getStartsAt()),
                            booking.getPriceAtBooking().toPlainString(), booking.getStatus());
        }
        if (item instanceof Customer customer) {
            return "Клиент #%s: %s [%s]".formatted(customer.getId(), customer.getName(),
                    customer.getCustomerCode());
        }
        if (item instanceof Event event) {
            return "Мероприятие #%s: %s | %s | %s | %s руб.".formatted(event.getId(), event.getTitle(),
                    event.getVenue(), DATE.format(event.getStartsAt()), event.getPrice().toPlainString());
        }
        if (item instanceof String text) {
            return text;
        }
        throw new IllegalArgumentException("Неподдерживаемый тип для консольного вывода");
    }
}
