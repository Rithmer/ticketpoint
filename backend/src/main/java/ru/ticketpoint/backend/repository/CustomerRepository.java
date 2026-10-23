package ru.ticketpoint.backend.repository;

import org.springframework.data.jpa.repository.*;
import ru.ticketpoint.backend.domain.Customer;

public interface CustomerRepository
        extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {
}
