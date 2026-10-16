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

    private final AccessPolicy access;

    public CustomerService(CustomerRepository customers) {
        this(customers, AccessPolicy.TRUSTED);
    }

    public CustomerService(CustomerRepository customers, AccessPolicy access) {
        this.access = Objects.requireNonNull(access);
        this.customers = Objects.requireNonNull(customers);
    }

    public Customer create(CreateCustomerCommand customer) {
        access.admin();
        Objects.requireNonNull(customer);
        String code = "CUS-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT);
        return customers.create(customer, code);
    }

    public Customer update(long id, CreateCustomerCommand command) {
        access.admin(); findById(id);
        return customers.update(id, command);
    }

    public void delete(long id) {
        access.admin();
        if (!customers.delete(Validation.id(id))) throw new EntityNotFoundException("Клиент не найден");
    }

    public List<Customer> findAll() {
        access.read().run();
        return access.personal() ? java.util.List.of(findById(access.customerId())) : customers.findAll();
    }

    public Customer findById(long id) {
        access.read().run();
        access.requireCustomer(id);
        return customers.findById(Validation.id(id))
                .orElseThrow(() -> new EntityNotFoundException("Клиент не найден"));
    }
}
