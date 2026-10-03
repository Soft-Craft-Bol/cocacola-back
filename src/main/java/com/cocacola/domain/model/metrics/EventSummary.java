package com.cocacola.domain.model.metrics;

import java.time.Instant;

public record EventSummary(
        String id, String name, String type, String campaign, Instant date, int registered, int attended,
        double attendanceRate, int interactions, int conversions, int redemptions, double satisfaction, double nps,
        double participationRate, double conversionRate) {
}
