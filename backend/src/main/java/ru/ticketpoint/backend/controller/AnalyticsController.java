package ru.ticketpoint.backend.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.*;
import ru.ticketpoint.backend.service.AnalyticsService;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        return analytics.summary();
    }
}
