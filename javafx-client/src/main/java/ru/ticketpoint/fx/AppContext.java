package ru.ticketpoint.fx;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import ru.ticketpoint.auth.*;
import ru.ticketpoint.config.*;
import ru.ticketpoint.model.Booking;
import ru.ticketpoint.model.BookingStatus;

public final class AppContext {
    private final ApplicationServices services;
    private final FxTaskExecutor tasks;
    private final AuthService auth;
    private volatile User user;
    private final Instant expires = Instant.now().plusSeconds(1800);

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
        if (Instant.now().isAfter(expires)) throw new AuthException("Сеанс завершён. Войдите заново");
        return user;
    }

    public ApplicationServices services() {
        return services;
    }

    @org.jetbrains.annotations.Contract(pure = true)
    public FxTaskExecutor getTasks() {
        return tasks;
    }

    public AuthService requireAuth() {
        if (auth == null) throw new AuthException("Войдите в систему");
        return auth;
    }

    @org.jetbrains.annotations.Contract(pure = true)
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

    public boolean isCustomer() {
        return user != null && user.role() == Role.USER;
    }

    public boolean canEdit(Booking booking) {
        if ((!canWrite() && !isCustomer()) || booking == null || booking.status() == BookingStatus.CANCELLED)
            return false;
        return !isCustomer() || (booking.status() != BookingStatus.PAID
                && booking.event().startsAt().isAfter(LocalDateTime.now(services.clock())));
    }

    public boolean canDelete(Booking booking) {
        return (canWrite() || isCustomer()) && booking != null && (booking.status() == BookingStatus.NEW
                || !isCustomer() && booking.status() == BookingStatus.CANCELLED);
    }

    public List<BookingStatus> statuses(Booking booking) {
        if (!isCustomer()) return booking.status().allowedTransitions();
        return booking.status() == BookingStatus.NEW
                ? List.of(BookingStatus.NEW, BookingStatus.CANCELLED)
                : List.of(BookingStatus.CANCELLED);
    }

    public void logout() {
        user = null;
        tasks.close();
    }
}
