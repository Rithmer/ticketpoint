package ru.ticketpoint.fx;

import ru.ticketpoint.auth.*;
import ru.ticketpoint.config.*;

import java.util.Objects;

public final class AppContext {
    private final ApplicationServices services;
    private final FxTaskExecutor tasks;
    private final AuthService auth;
    private volatile User user;
    private final java.time.Instant expires = java.time.Instant.now().plusSeconds(1800);

    public AppContext(ApplicationServices services, FxTaskExecutor tasks) {
        this.services = Objects.requireNonNull(services);
        this.tasks = Objects.requireNonNull(tasks);
        auth = null;
    }

    public AppContext(DatabaseConfig config, FxTaskExecutor tasks, AuthService auth, User user) {
        this.tasks = tasks;
        this.auth = auth;
        this.user = user;
        services = ApplicationServices.secured(config, auth, this::sessionUser);
        tasks.beforeOperation(() -> auth.current(sessionUser()));
    }

    private User sessionUser() {
        if (java.time.Instant.now().isAfter(expires)) throw new AuthException("Сеанс завершён. Войдите заново");
        return user;
    }

    public ApplicationServices services() {
        return services;
    }

    public FxTaskExecutor tasks() {
        return tasks;
    }

    public AuthService auth() {
        return auth;
    }

    public User user() {
        return user;
    }

    public void user(User value) {
        user = value;
    }

    public boolean canWrite() {
        return auth == null || user != null && user.role().canWrite();
    }

    public boolean isAdmin() {
        return auth != null && user != null && user.role() == Role.ADMIN;
    }
    public boolean isCustomer() { return user != null && user.role() == Role.USER; }

    public boolean canEdit(ru.ticketpoint.model.Booking booking) {
        if (booking == null || booking.status() == ru.ticketpoint.model.BookingStatus.CANCELLED) return false;
        return !isCustomer() || (booking.status() != ru.ticketpoint.model.BookingStatus.PAID
                && booking.event().startsAt().isAfter(java.time.LocalDateTime.now(services.clock())));
    }

    public boolean canDelete(ru.ticketpoint.model.Booking booking) {
        return booking != null && (booking.status() == ru.ticketpoint.model.BookingStatus.NEW
                || !isCustomer() && booking.status() == ru.ticketpoint.model.BookingStatus.CANCELLED);
    }

    public java.util.List<ru.ticketpoint.model.BookingStatus> statuses(ru.ticketpoint.model.Booking booking) {
        if (!isCustomer()) return booking.status().allowedTransitions();
        return booking.status() == ru.ticketpoint.model.BookingStatus.NEW
                ? java.util.List.of(ru.ticketpoint.model.BookingStatus.NEW, ru.ticketpoint.model.BookingStatus.CANCELLED)
                : java.util.List.of(ru.ticketpoint.model.BookingStatus.CANCELLED);
    }

    public void logout() {
        user = null;
        tasks.close();
    }
}
