package ru.ticketpoint.backend.repository;

import java.util.*;

import org.springframework.data.jpa.repository.*;
import ru.ticketpoint.backend.domain.AuditEvent;

public interface AuditEventRepository
        extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {
}
