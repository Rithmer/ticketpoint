package ru.ticketpoint.fx;

import javafx.util.StringConverter;
import ru.ticketpoint.model.BookingStatus;
import ru.ticketpoint.model.Customer;
import ru.ticketpoint.model.Event;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

final class UiFormat {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private UiFormat() { }
    static String customer(Customer customer) { return customer.name() + " • " + customer.customerCode(); }
    static String event(Event event) { return event.title() + " • " + date(event.startsAt()); }

    static String date(LocalDateTime value) { return DATE_TIME.format(value); }

    static String status(BookingStatus value) {
        if (value == null) {
            return "Все статусы";
        }
        return switch (value) {
            case NEW -> "Новое";
            case PAID -> "Оплачено";
            case CANCELLED -> "Отменено";
        };
    }

    static <T> StringConverter<T> converter(Function<T, String> format, String empty) {
        return new StringConverter<>() {
            @Override public String toString(T value) { return value == null ? empty : format.apply(value); }
            @Override public T fromString(String value) { throw new UnsupportedOperationException(); }
        };
    }

}
