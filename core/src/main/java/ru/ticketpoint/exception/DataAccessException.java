package ru.ticketpoint.exception;

import java.io.Serial;

public final class DataAccessException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
