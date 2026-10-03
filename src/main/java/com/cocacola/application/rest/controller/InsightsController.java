package com.cocacola.application.rest.controller;

import com.cocacola.domain.model.insights.Insights.AffinityReport;
import com.cocacola.domain.model.insights.Insights.AttendanceForecast;
import com.cocacola.domain.model.insights.Insights.InsightSummary;
import com.cocacola.domain.model.insights.Insights.Recommendation;
import com.cocacola.domain.model.insights.Insights.Segment;
import com.cocacola.domain.service.InsightsService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Inteligencia: segmentos, afinidad, predicción, recomendaciones y resumen con IA. */
@RestController
@RequestMapping("/insights")
@RequiredArgsConstructor
public class InsightsController {

    private final InsightsService insights;

    @GetMapping("/segments")
    public List<Segment> segments(@RequestParam(required = false) String eventId) {
        return insights.segments(eventId);
    }

    @GetMapping("/affinity")
    public AffinityReport affinity(@RequestParam(required = false) String eventId, @RequestParam(defaultValue = "20") int limit) {
        return insights.affinity(eventId, limit);
    }

    @GetMapping("/forecast")
    public List<AttendanceForecast> forecast() {
        return insights.forecast();
    }

    @GetMapping("/recommendations")
    public List<Recommendation> recommendations(@RequestParam(required = false) String eventId) {
        return insights.recommendations(eventId);
    }

    @GetMapping("/summary")
    public InsightSummary summary(@RequestParam(required = false) String eventId, @RequestParam(defaultValue = "false") boolean ai) {
        return insights.summary(eventId, ai);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("aiConfigured", insights.aiConfigured());
    }
}
