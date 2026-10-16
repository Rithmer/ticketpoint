package ru.ticketpoint.auth;

import java.io.Serial;

public final class AuthException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;
    private final boolean forbidden;
    public AuthException(String message) { this(message, false); }
    public AuthException(String message, boolean forbidden) { super(message); this.forbidden = forbidden; }
    public boolean forbidden() { return forbidden; }
}
