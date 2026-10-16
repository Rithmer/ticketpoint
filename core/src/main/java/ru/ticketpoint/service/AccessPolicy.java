package ru.ticketpoint.service;

import ru.ticketpoint.auth.AuthException;
import ru.ticketpoint.auth.Role;
import ru.ticketpoint.auth.User;
import java.util.function.Supplier;

public record AccessPolicy(Runnable read, Runnable write, Supplier<User> actor) {
    public AccessPolicy(Runnable read, Runnable write) { this(read, write, () -> null); }
    public static final AccessPolicy TRUSTED = new AccessPolicy(() -> { }, () -> { });

    public boolean personal() { return actor.get() != null && actor.get().role() == Role.USER; }
    public Long customerId() {
        if (!personal()) return null;
        Long id = actor.get().customerId();
        if (id == null) throw new AuthException("Профиль покупателя не настроен. Обратитесь к администратору", true);
        return id;
    }
    public void requireCustomer(long id) {
        read.run();
        if (personal() && customerId() != id) throw new AuthException("Доступ разрешён только к своим бронированиям", true);
    }
    public void admin() {
        read.run();
        User user = actor.get();
        if (user != null && user.role() != Role.ADMIN) throw new AuthException("Доступ разрешён только администратору", true);
    }
}
