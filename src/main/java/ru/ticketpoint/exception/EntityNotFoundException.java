package ru.ticketpoint.exception;

import java.io.Serial;

public final class EntityNotFoundException extends BookingException {
    @Serial private static final long serialVersionUID = 1L;
    public EntityNotFoundException(String message) { super(message); }
}
