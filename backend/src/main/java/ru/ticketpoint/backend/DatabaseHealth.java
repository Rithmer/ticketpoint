package ru.ticketpoint.backend;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import ru.ticketpoint.config.ApplicationServices;

@Component
public final class DatabaseHealth implements HealthIndicator {
    private final ApplicationServices services;
    public DatabaseHealth(ApplicationServices services) { this.services = services; }

    @Override public Health health() {
        try {
            services.preflight().verify();
            return Health.up().build();
        } catch (java.sql.SQLException | RuntimeException error) {
            return Health.down().build();
        }
    }
}
