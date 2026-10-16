package ru.ticketpoint.model;

import ru.ticketpoint.exception.BookingException;
import java.util.Arrays;
import java.util.List;

public enum BookingStatus {
    NEW, CONFIRMED, PAID, CANCELLED;

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
        if (this == NEW && next == PAID) return "Перед отметкой об оплате менеджер должен подтвердить заявку";
        if (this == PAID && next != PAID && next != CANCELLED)
            return "Оплаченное бронирование можно только отменить";
        return null;
    }
}
