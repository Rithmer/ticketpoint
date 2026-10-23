package ru.ticketpoint.backend;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import ru.ticketpoint.config.EnvironmentFile;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class TicketPointBackendApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(TicketPointBackendApplication.class);
        Map<String, Object> defaults = new HashMap<>(EnvironmentFile.read(Path.of(".env"), System.getenv()));
        app.setDefaultProperties(defaults);
        app.run(args);
    }
}
