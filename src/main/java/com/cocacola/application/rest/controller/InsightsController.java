package com.cocacola.application.rest.controller;

import com.cocacola.domain.model.insights.Insights.AffinityReport;
import com.cocacola.domain.model.insights.Insights.AttendanceForecast;
import com.cocacola.domain.model.insights.Insights.InsightSummary;
import com.cocacola.domain.model.insights.Insights.Recommendation;
import com.cocacola.domain.model.insights.Insights.Segment;
import com.cocacola.domain.service.InsightsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final com.cocacola.utils.SpeechSynthesizer speech;

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

    @GetMapping("/predictions/analysis")
    public com.cocacola.domain.model.insights.Insights.PredictionAnalysis predictionAnalysis(@RequestParam(required = false) String eventId) {
        return insights.predictionAnalysis(eventId);
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("aiConfigured", insights.aiConfigured(), "narration", speech.provider());
    }

    /** Narración: devuelve el audio MP3 del texto; 503 si no hay voz configurada (el frontend usa la del navegador). */
    @PostMapping("/narrate")
    public ResponseEntity<?> narrate(@RequestBody NarrateRequest request) {
        String text = request.text() == null ? "" : request.text().trim();
        if (text.isEmpty()) throw new IllegalArgumentException("No hay texto para narrar");
        return speech.synthesize(text)
                .<ResponseEntity<?>>map(audio -> ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg")).body(audio))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message", "La voz del servidor no está configurada o no respondió")));
    }

    public record NarrateRequest(String text) {}
}
