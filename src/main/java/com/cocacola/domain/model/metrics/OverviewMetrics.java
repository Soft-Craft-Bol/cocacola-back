package com.cocacola.domain.model.metrics;

import java.util.List;

/** Indicadores globales de todos los eventos (lectura). */
public record OverviewMetrics(
        int events,
        int registered,
        int attended,
        double attendanceRate,
        int productInteractions,
        int samples,
        int conversions,
        int redemptions,
        int consents,
        int returning,
        double recurrenceIndex,
        double satisfaction,
        double nps,
        List<EventSummary> perEvent,
        List<NamedValue> byCity,
        List<NamedValue> byAge,
        List<NamedValue> bySource,
        List<NamedValue> byCampaign,
        List<NamedValue> consentSplit,
        List<NamedValue> newVsReturning,
        List<NamedValue> loyalty,
        List<ProductInterest> productInterest,
        List<EvolutionPoint> evolution) {
}
