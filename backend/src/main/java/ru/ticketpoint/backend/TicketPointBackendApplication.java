package ru.ticketpoint.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import ru.ticketpoint.config.ApplicationServices;
import ru.ticketpoint.config.DatabaseConfig;

@SpringBootApplication
public class TicketPointBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketPointBackendApplication.class, args);
    }

    @Bean
    ru.ticketpoint.auth.AuthService authService() throws java.sql.SQLException {
        DatabaseConfig config = DatabaseConfig.fromEnvironment();
        var connections = new ru.ticketpoint.config.JdbcConnectionProvider(config);
        new ru.ticketpoint.service.DatabasePreflight(connections).verify();
        var auth = new ru.ticketpoint.auth.AuthService(new ru.ticketpoint.repository.JdbcUserRepository(connections),
                java.time.Clock.system(config.zone()));
        auth.initialize(ru.ticketpoint.auth.AuthConfig.fromEnvironment());
        return auth;
    }

    @Bean
    ApplicationServices applicationServices(ru.ticketpoint.auth.AuthService auth) {
        return ApplicationServices.secured(DatabaseConfig.fromEnvironment(), auth, () -> {
            var attributes = (org.springframework.web.context.request.ServletRequestAttributes)
                    org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes();
            return AuthController.user(attributes.getRequest());
        });
    }
}
