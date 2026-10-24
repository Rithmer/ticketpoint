package ru.ticketpoint.backend.repository;

import jakarta.persistence.LockModeType;

import java.util.*;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.ticketpoint.backend.domain.UserAccount;

public interface UserAccountRepository
        extends JpaRepository<UserAccount, Long>, JpaSpecificationExecutor<UserAccount> {
    Optional<UserAccount> findByPublicId(UUID id);

    Optional<UserAccount> findByLoginIgnoreCase(String login);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where lower(u.login)=lower(:login)")
    Optional<UserAccount> lockByLogin(@Param("login") String login);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.enabled=true and u.role='ADMIN' order by u.id")
    List<UserAccount> lockEnabledAdmins();
}
