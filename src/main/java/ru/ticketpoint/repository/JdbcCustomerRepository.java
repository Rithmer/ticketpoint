package ru.ticketpoint.repository;

import ru.ticketpoint.config.ConnectionProvider;
import ru.ticketpoint.model.CreateCustomerCommand;
import ru.ticketpoint.model.Customer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class JdbcCustomerRepository implements CustomerRepository {
    private final ConnectionProvider connections;

    public JdbcCustomerRepository(ConnectionProvider connections) {
        this.connections = Objects.requireNonNull(connections);
    }

    @Override
    public Customer create(CreateCustomerCommand customer, String customerCode) {
        String sql = "INSERT INTO customers(customer_code, name) VALUES (?, ?) "
                + "RETURNING id, customer_code, name";
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, Customer.normalizeCode(customerCode));
            statement.setString(2, customer.name());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException("INSERT клиента не вернул созданную запись");
                }
                return map(result);
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate("Не удалось создать клиента", e);
        }
    }

    @Override
    public List<Customer> findAll() {
        return query("SELECT id, customer_code, name FROM customers ORDER BY id", null);
    }

    @Override
    public Optional<Customer> findById(long id) {
        String sql = "SELECT id, customer_code, name FROM customers WHERE id = ?";
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(map(result)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate("Не удалось прочитать клиента", e);
        }
    }

    private List<Customer> query(String sql, Long id) {
        try (Connection connection = connections.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            if (id != null) {
                statement.setLong(1, id);
            }
            try (ResultSet result = statement.executeQuery()) {
                List<Customer> customers = new ArrayList<>();
                while (result.next()) {
                    customers.add(map(result));
                }
                return List.copyOf(customers);
            }
        } catch (SQLException e) {
            throw SqlExceptionTranslator.translate("Не удалось прочитать клиентов", e);
        }
    }

    private static Customer map(ResultSet result) throws SQLException {
        return new Customer(result.getLong("id"), result.getString("customer_code"), result.getString("name"));
    }
}
