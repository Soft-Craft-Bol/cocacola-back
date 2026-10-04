package com.cocacola.domain.model.insights;

import com.cocacola.domain.model.metrics.NamedValue;
import java.util.List;

/** Resultados del motor de inteligencia (lectura). */
public final class Insights {

    private Insights() {
    }

    public record SegmentMember(String participantId, String name, String city, boolean consent, int affinity) {
    }

    public record Segment(String key, String name, String description, String action, int count, double pct,
                          List<SegmentMember> members) {
    }

    public record AffinityScore(String participantId, String name, String city, boolean consent, int score, String level,
                                List<String> factors) {
    }

    public record AffinityReport(List<NamedValue> distribution, double averageScore, List<AffinityScore> top) {
    }

    public record SourceForecast(String source, int registered, double rate, double expected) {
    }

    public record AttendanceForecast(String eventId, String eventName, String status, int registered, int attendedSoFar,
                                     Integer expectedAttendees, Integer low, Integer high, Double historicalRate,
                                     Integer projectedConversions, Integer targetExpected, List<SourceForecast> bySource,
                                     String note) {
    }

    public record Recommendation(String priority, String category, String title, String detail) {
    }

    public record PredictionAnalysis(String text, String source) {
    }

    public record InsightSummary(String eventId, String title, String text, List<Recommendation> recommendations,
                                 String ai, String aiSource) {
    }
}
