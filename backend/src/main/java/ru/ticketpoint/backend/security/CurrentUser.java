package ru.ticketpoint.backend.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import ru.ticketpoint.backend.domain.UserAccount;
import ru.ticketpoint.backend.error.ApiException;
import ru.ticketpoint.backend.repository.UserAccountRepository;

@Service
public class CurrentUser {
    private final UserAccountRepository users;

    public CurrentUser(UserAccountRepository users) {
        this.users = users;
    }

    public UserAccount get() {
        return optional().orElseThrow(ApiException::unauthorized);
    }

    public Optional<UserAccount> optional() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID id))
            return Optional.empty();
        return users.findByPublicId(id);
    }
}
