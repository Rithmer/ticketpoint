package ru.ticketpoint.backend.service;

import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.context.request.*;
import ru.ticketpoint.backend.domain.AuditEvent;
import ru.ticketpoint.backend.repository.AuditEventRepository;
import ru.ticketpoint.backend.repository.UserAccountRepository;

@Service
public class AuditService {
    private final AuditEventRepository events;
    private final UserAccountRepository users;

    public AuditService(
            AuditEventRepository events, UserAccountRepository users) {
        this.events = events;
        this.users = users;
    }

    @Transactional
    public void record(String type, String entity, UUID id, String summary) {
        var a = new AuditEvent();
        a.setEventType(type);
        a.setEntityType(entity);
        a.setEntityId(id);
        a.setSummary(summary);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        a.setActor(auth == null ? "anonymous" : auth.getName());
        UUID actor =
                auth != null && auth.getPrincipal() instanceof UUID u
                        ? u
                        : ("USER".equals(entity) ? id : null);
        if (actor != null) {
            a.setActorId(actor);
            users.findByPublicId(actor).ifPresent(u -> a.setActor(u.getLogin()));
        }
        var attrs = RequestContextHolder.getRequestAttributes();
        a.setCorrelationId(attrs instanceof ServletRequestAttributes s
                ? String.valueOf(s.getRequest().getAttribute("correlationId"))
                : UUID.randomUUID().toString());
        events.save(a);
    }
}
