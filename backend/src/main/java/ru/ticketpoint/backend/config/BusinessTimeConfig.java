package ru.ticketpoint.backend.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessTimeConfig {
    @Bean
    Clock businessClock(@Value("${APP_ZONE:Europe/Moscow}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }
}
