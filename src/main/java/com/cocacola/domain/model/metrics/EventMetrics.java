package com.cocacola.domain.model.metrics;

import com.cocacola.domain.model.Event;
import java.util.List;

/** Indicadores de un evento (lectura). */
public record EventMetrics(
        Event event,
        int registered,
        int attended,
        double attendanceRate,
        int newCount,
        int returningCount,
        double recurrenceIndex,
        int productInteractions,
        int samples,
        double participationRate,
        int consents,
        double consentRate,
        int conversions,
        double conversionRate,
        int redemptions,
        double interactionRate,
        double avgStayMinutes,
        double satisfaction,
        List<CriterionScore> satisfactionByCriterion,
        double nps,
        int surveyCount,
        List<ProductInterest> productInterest,
        List<NamedValue> activityPerformance,
        List<NamedValue> hourly,
        List<NamedValue> funnel,
        List<NamedValue> byCity,
        List<NamedValue> byAge,
        List<NamedValue> bySource,
        List<NamedValue> byCampaign) {
}
