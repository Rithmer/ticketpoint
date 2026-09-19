package ru.ticketpoint.service;

import ru.ticketpoint.exception.EntityNotFoundException;
import ru.ticketpoint.model.CreateCustomerCommand;
import ru.ticketpoint.model.Customer;
import ru.ticketpoint.model.Validation;
import ru.ticketpoint.repository.CustomerRepository;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public final class CustomerService {
    private final CustomerRepository customers;

    public CustomerService(CustomerRepository customers) {
        this.customers = Objects.requireNonNull(customers);
    }

    public Customer create(CreateCustomerCommand customer) {
        Objects.requireNonNull(customer);
        String code = "CUS-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        return customers.create(customer, code);
    }

    public List<Customer> findAll() {
        return customers.findAll();
    }

    public Customer findById(long id) {
        return customers.findById(Validation.id(id))
                .orElseThrow(() -> new EntityNotFoundException("Клиент не найден"));
    }
}
