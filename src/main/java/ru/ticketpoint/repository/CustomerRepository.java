package ru.ticketpoint.repository;

import ru.ticketpoint.model.CreateCustomerCommand;
import ru.ticketpoint.model.Customer;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository {
    Customer create(CreateCustomerCommand customer, String customerCode);
    List<Customer> findAll();
    Optional<Customer> findById(long id);
}
