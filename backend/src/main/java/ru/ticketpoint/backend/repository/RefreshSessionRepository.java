package ru.ticketpoint.backend.repository;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.*;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.ticketpoint.backend.domain.RefreshSession;

public interface RefreshSessionRepository
        extends JpaRepository<RefreshSession, UUID>, JpaSpecificationExecutor<RefreshSession> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RefreshSession s where s.tokenHash=:hash")
    Optional<RefreshSession> lockByHash(@Param("hash") String hash);

    @Modifying
    @Query(
            "update RefreshSession s set s.revokedAt=:now where s.user.id=:userId and s.revokedAt is"
                    + " null")
    void revokeAll(@Param("userId") Long userId, @Param("now") Instant now);
}
