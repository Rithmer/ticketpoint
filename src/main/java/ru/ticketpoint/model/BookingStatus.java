package ru.ticketpoint.model;

import ru.ticketpoint.exception.BookingException;
import java.util.Arrays;
import java.util.List;

public enum BookingStatus {
    NEW, PAID, CANCELLED;

    public boolean canTransitionTo(BookingStatus next) {
        return transitionError(next) == null;
    }

    public List<BookingStatus> allowedTransitions() {
        return Arrays.stream(values()).filter(this::canTransitionTo).toList();
    }

    public void validateTransitionTo(BookingStatus next) {
        String error = transitionError(next);
        if (error != null) {
            throw new BookingException(error);
        }
    }

    private String transitionError(BookingStatus next) {
        if (next == null) {
            return "Новый статус обязателен";
        }
        if (this == CANCELLED) {
            return "Отменённое бронирование нельзя изменить";
        }
        if (this == PAID && next == NEW) {
            return "Оплаченное бронирование нельзя вернуть в статус «Новое»";
        }
        return null;
    }
}
