package ru.ticketpoint.backend.repository;

import java.util.*;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import ru.ticketpoint.backend.domain.BookingStatus;
import ru.ticketpoint.backend.domain.Booking;

public interface BookingRepository
        extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {
    Optional<Booking> findByPublicId(UUID id);

    Optional<Booking> findByTicketNumberIgnoreCase(String code);

    @Query("select b from Booking b where upper(b.ticketNumber) in :codes")
    List<Booking> findByTicketNumbersIgnoreCase(@Param("codes") Collection<String> codes);

    @Query("""
            select count(b),
                   coalesce(sum(case when b.archived = false then 1L else 0L end), 0L),
                   coalesce(sum(case when b.archived = true then 1L else 0L end), 0L),
                   coalesce(sum(case when b.vip = true then 1L else 0L end), 0L),
                   sum(b.price)
            from Booking b
            where (:ownerId = 0 or b.customer.id = :ownerId)
            """)
    List<Object[]> analyticsTotals(@Param("ownerId") long ownerId);

    @Query("""
            select e.publicId, e.title, count(b)
            from Booking b join b.event e
            where (:ownerId = 0 or b.customer.id = :ownerId)
            group by e.publicId, e.title
            """)
    List<Object[]> analyticsByEvent(@Param("ownerId") long ownerId);

    @Query("""
            select b.status, count(b)
            from Booking b
            where (:ownerId = 0 or b.customer.id = :ownerId)
            group by b.status
            """)
    List<Object[]> analyticsByStatus(@Param("ownerId") long ownerId);

    @Query("""
            select count(b)
            from Booking b
            where (:ownerId = 0 or b.customer.id = :ownerId)
              and b.status in :pending and b.vip = true
            """)
    long countHighPriority(@Param("ownerId") long ownerId, @Param("pending") Collection<BookingStatus> pending);

    @Query("""
            select count(b)
            from Booking b
            where (:ownerId = 0 or b.customer.id = :ownerId)
              and b.status in :pending and b.vip = false and b.price >= 5000
            """)
    long countMediumPriority(@Param("ownerId") long ownerId, @Param("pending") Collection<BookingStatus> pending);
}
