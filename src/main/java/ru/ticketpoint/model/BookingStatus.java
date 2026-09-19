package ru.ticketpoint.model;

import ru.ticketpoint.exception.BookingException;

public enum BookingStatus {
    NEW, PAID, CANCELLED;

    public void validateTransitionTo(BookingStatus next) {
        if (next == null) {
            throw new BookingException("Новый статус обязателен");
        }
        if (this == CANCELLED) {
            throw new BookingException("Отменённое бронирование нельзя изменить");
        }
        if (this == PAID && next == NEW) {
            throw new BookingException("Оплаченное бронирование нельзя вернуть в статус NEW");
        }
    }
}
