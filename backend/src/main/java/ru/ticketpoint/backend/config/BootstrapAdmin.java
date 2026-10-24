package ru.ticketpoint.backend.config;

import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.ticketpoint.auth.*;
import ru.ticketpoint.backend.domain.UserAccount;
import ru.ticketpoint.backend.repository.UserAccountRepository;

@Component
public class BootstrapAdmin implements CommandLineRunner {
    private final UserAccountRepository users;
    private final String login, name, password;

    public BootstrapAdmin(
            UserAccountRepository users,
            @Value("${BOOTSTRAP_ADMIN_LOGIN:admin}") String login,
            @Value("${BOOTSTRAP_ADMIN_NAME:Администратор}") String name,
            @Value("${BOOTSTRAP_ADMIN_PASSWORD:}") String password) {
        this.users = users;
        this.login = login;
        this.name = name;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(@NonNull String @NonNull ... args) {
        if (users.count() != 0) return;
        var u = new UserAccount();
        u.setLogin(AuthService.login(login));
        u.setName(name);
        u.setPasswordHash(new PasswordHasher().hash(password));
        u.setRole("ADMIN");
        users.save(u);
    }
}
