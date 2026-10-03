package com.cocacola.application.rest.controller;

import com.cocacola.domain.model.metrics.EventMetrics;
import com.cocacola.domain.model.metrics.OverviewMetrics;
import com.cocacola.domain.service.MetricsService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metrics;

    @GetMapping("/overview")
    public OverviewMetrics overview() {
        return metrics.overview();
    }

    @GetMapping("/events/{id}")
    public EventMetrics event(@PathVariable String id) {
        return metrics.forEvent(id);
    }
}
