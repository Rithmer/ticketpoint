package ru.ticketpoint.repository;

import ru.ticketpoint.config.ConnectionProvider;
import ru.ticketpoint.model.CreateCustomerCommand;
import ru.ticketpoint.model.Customer;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public final class JdbcCustomerRepository implements CustomerRepository {
    private final JdbcQueries queries;

    public JdbcCustomerRepository(ConnectionProvider connections) {
        this.queries = new JdbcQueries(connections);
    }

    @Override
    public Customer create(CreateCustomerCommand customer, String customerCode) {
        String sql = "INSERT INTO customers(customer_code, name) VALUES (?, ?) "
                + "RETURNING id, customer_code, name";
        String code = Customer.normalizeCode(customerCode);
        return queries.one(connection -> connection.prepareStatement(sql), statement -> {
            statement.setString(1, code);
            statement.setString(2, customer.name());
        }, JdbcCustomerRepository::map, "Не удалось создать клиента")
                .orElseThrow(() -> new IllegalStateException("INSERT клиента не вернул созданную запись"));
    }

    @Override
    public List<Customer> findAll() {
        return queries.list(connection -> connection.prepareStatement("SELECT id, customer_code, name FROM customers ORDER BY id"),
                JdbcQueries.Parameters.NONE, JdbcCustomerRepository::map, "Не удалось прочитать клиентов");
    }

    @Override
    public Optional<Customer> findById(long id) {
        String sql = "SELECT id, customer_code, name FROM customers WHERE id = ?";
        return queries.one(connection -> connection.prepareStatement(sql), statement -> statement.setLong(1, id),
                JdbcCustomerRepository::map, "Не удалось прочитать клиента");
    }

    private static Customer map(ResultSet result) throws SQLException {
        return new Customer(result.getLong("id"), result.getString("customer_code"), result.getString("name"));
    }
}
