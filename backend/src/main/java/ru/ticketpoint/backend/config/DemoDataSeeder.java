package ru.ticketpoint.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import ru.ticketpoint.auth.PasswordHasher;

import java.util.List;

@Component
@ConditionalOnProperty(name = "ticketpoint.demo-seed.enabled", havingValue = "true")
public class DemoDataSeeder implements CommandLineRunner {
    private static final List<DemoUser> USERS = List.of(
            new DemoUser("demo-admin", "Демо Администратор", "ADMIN", "DemoAdmin123!", null),
            new DemoUser("demo-manager", "Демо Менеджер", "MANAGER", "DemoManager123!", null),
            new DemoUser("demo-viewer", "Демо Наблюдатель", "VIEWER", "DemoViewer123!", null),
            new DemoUser("demo-buyer", "Демо Покупатель", "USER", "DemoBuyer123!", "CUS-00000000-0000-0000-0000-000000000004"));

    private final JdbcTemplate jdbc;
    private final ConfigurableApplicationContext context;
    private final TransactionTemplate transaction;
    private final PasswordHasher passwords = new PasswordHasher();

    public DemoDataSeeder(
            JdbcTemplate jdbc,
            ConfigurableApplicationContext context,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.context = context;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(String... args) {
        transaction.executeWithoutResult(status -> seed());
        System.out.println("TicketPoint demo data is ready. Use the demo accounts listed in README.md.");
        SpringApplication.exit(context);
    }

    private void seed() {
        seedCustomers();
        seedEvents();
        seedBookings();
        seedUsers();
        seedTags();
        revokeDemoSessions();
    }

    private void seedCustomers() {
        jdbc.update("""
                INSERT INTO customers(customer_code, name) VALUES
                  ('CUS-00000000-0000-0000-0000-000000000001', 'Анна Смирнова'),
                  ('CUS-00000000-0000-0000-0000-000000000002', 'Иван Петров'),
                  ('CUS-00000000-0000-0000-0000-000000000003', 'Мария Соколова'),
                  ('CUS-00000000-0000-0000-0000-000000000004', 'Демо Покупатель'),
                  ('CUS-00000000-0000-0000-0000-000000000005', 'Алексей Волков'),
                  ('CUS-00000000-0000-0000-0000-000000000006', 'Елена Кузнецова')
                ON CONFLICT (customer_code) DO UPDATE SET name = EXCLUDED.name
                """);
    }

    private void seedEvents() {
        jdbc.update("""
                INSERT INTO events(title, venue, starts_at, price)
                SELECT v.title, v.venue, CURRENT_DATE + v.days * INTERVAL '1 day' + INTERVAL '19 hours', v.price
                FROM (VALUES ('Джазовый концерт', 'Большой зал', 30, 2500.00),
                             ('Театральная премьера', 'Городской театр', 45, 6000.00),
                             ('Музыкальный фестиваль', 'Открытая сцена', 60, 8500.00))
                     AS v(title, venue, days, price)
                WHERE NOT EXISTS (SELECT 1 FROM events e WHERE e.title = v.title AND e.venue = v.venue)
                """);
    }

    private void seedBookings() {
        jdbc.update("""
                INSERT INTO bookings(ticket_number, customer_id, event_id, price_at_booking, status, created_at, vip, archived, description)
                SELECT v.ticket_number, c.id, e.id, e.price, v.status::booking_status,
                       LOCALTIMESTAMP - v.days_ago * INTERVAL '1 day', v.vip, v.archived, v.description
                FROM (VALUES
                    ('DEMO-TP-0001', 'CUS-00000000-0000-0000-0000-000000000001', 'Джазовый концерт', 'Большой зал', 'NEW', 10, TRUE, FALSE, 'VIP-бронирование для демонстрации'),
                    ('DEMO-TP-0002', 'CUS-00000000-0000-0000-0000-000000000002', 'Театральная премьера', 'Городской театр', 'CONFIRMED', 9, FALSE, FALSE, 'Подтверждено менеджером'),
                    ('DEMO-TP-0003', 'CUS-00000000-0000-0000-0000-000000000003', 'Музыкальный фестиваль', 'Открытая сцена', 'PAID', 8, TRUE, FALSE, 'Оплаченное бронирование'),
                    ('DEMO-TP-0004', 'CUS-00000000-0000-0000-0000-000000000004', 'Джазовый концерт', 'Большой зал', 'NEW', 7, FALSE, FALSE, 'Бронирование демо-покупателя'),
                    ('DEMO-TP-0005', 'CUS-00000000-0000-0000-0000-000000000005', 'Театральная премьера', 'Городской театр', 'CANCELLED', 6, FALSE, FALSE, 'Отменённая запись'),
                    ('DEMO-TP-0006', 'CUS-00000000-0000-0000-0000-000000000006', 'Музыкальный фестиваль', 'Открытая сцена', 'PAID', 5, FALSE, FALSE, 'Оплачено'),
                    ('DEMO-TP-0007', 'CUS-00000000-0000-0000-0000-000000000001', 'Джазовый концерт', 'Большой зал', 'CONFIRMED', 4, FALSE, FALSE, 'Подтверждено'),
                    ('DEMO-TP-0008', 'CUS-00000000-0000-0000-0000-000000000002', 'Театральная премьера', 'Городской театр', 'NEW', 3, TRUE, FALSE, 'Высокий приоритет'),
                    ('DEMO-TP-0009', 'CUS-00000000-0000-0000-0000-000000000003', 'Музыкальный фестиваль', 'Открытая сцена', 'CANCELLED', 2, FALSE, TRUE, 'Архивная запись для проверки восстановления'),
                    ('DEMO-TP-0010', 'CUS-00000000-0000-0000-0000-000000000004', 'Театральная премьера', 'Городской театр', 'PAID', 1, FALSE, FALSE, 'Билет демо-покупателя')
                ) AS v(ticket_number, customer_code, title, venue, status, days_ago, vip, archived, description)
                JOIN customers c ON c.customer_code = v.customer_code
                JOIN events e ON e.title = v.title AND e.venue = v.venue
                ON CONFLICT (ticket_number) DO UPDATE SET
                    customer_id = EXCLUDED.customer_id, event_id = EXCLUDED.event_id,
                    price_at_booking = EXCLUDED.price_at_booking, status = EXCLUDED.status,
                    vip = EXCLUDED.vip, archived = EXCLUDED.archived, description = EXCLUDED.description
                """);
    }

    private void seedUsers() {
        for (DemoUser user : USERS) {
            jdbc.update("""
                    INSERT INTO app_users(login, name, password_hash, role, enabled, password_change_required, customer_id)
                    VALUES (?, ?, ?, ?, TRUE, FALSE,
                            (SELECT id FROM customers WHERE customer_code = ?))
                    ON CONFLICT (login) DO UPDATE SET
                        name = EXCLUDED.name, password_hash = EXCLUDED.password_hash,
                        role = EXCLUDED.role, enabled = TRUE, password_change_required = FALSE,
                        customer_id = EXCLUDED.customer_id, failed_attempts = 0, locked_until = NULL,
                        version = app_users.version + 1
                    """, user.login(), user.name(), passwords.hash(user.password()), user.role(), user.customerCode());
        }
    }

    private void seedTags() {
        jdbc.update("""
                INSERT INTO booking_tags(booking_id, tag)
                SELECT id, 'демо' FROM bookings WHERE ticket_number LIKE 'DEMO-TP-%'
                ON CONFLICT DO NOTHING
                """);
        jdbc.update("""
                INSERT INTO booking_tags(booking_id, tag)
                SELECT id, 'vip' FROM bookings WHERE ticket_number IN ('DEMO-TP-0001', 'DEMO-TP-0003', 'DEMO-TP-0008')
                ON CONFLICT DO NOTHING
                """);
    }

    private void revokeDemoSessions() {
        jdbc.update("""
                UPDATE refresh_sessions SET revoked_at = CURRENT_TIMESTAMP
                WHERE revoked_at IS NULL AND user_id IN (SELECT id FROM app_users WHERE login LIKE 'demo-%')
                """);
    }

    private record DemoUser(String login, String name, String role, String password, String customerCode) {
    }
}
