package com.cocacola.domain.service;

import com.cocacola.commons.enums.EventStatus;
import com.cocacola.commons.enums.InteractionType;
import com.cocacola.domain.helpers.NotFoundException;
import com.cocacola.domain.model.Event;
import com.cocacola.domain.model.Interaction;
import com.cocacola.domain.model.Participant;
import com.cocacola.domain.model.Survey;
import com.cocacola.domain.model.insights.Insights.AffinityReport;
import com.cocacola.domain.model.insights.Insights.AffinityScore;
import com.cocacola.domain.model.insights.Insights.AttendanceForecast;
import com.cocacola.domain.model.insights.Insights.InsightSummary;
import com.cocacola.domain.model.insights.Insights.PredictionAnalysis;
import com.cocacola.domain.model.insights.Insights.Recommendation;
import com.cocacola.domain.model.insights.Insights.Segment;
import com.cocacola.domain.model.insights.Insights.SegmentMember;
import com.cocacola.domain.model.insights.Insights.SourceForecast;
import com.cocacola.domain.model.metrics.CriterionScore;
import com.cocacola.domain.model.metrics.EventMetrics;
import com.cocacola.domain.model.metrics.NamedValue;
import com.cocacola.domain.model.metrics.OverviewMetrics;
import com.cocacola.domain.repository.EventRepository;
import com.cocacola.domain.repository.InteractionRepository;
import com.cocacola.domain.repository.ParticipantRepository;
import com.cocacola.domain.repository.SurveyRepository;
import com.cocacola.domain.repository.TextGenerator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Motor de inteligencia: segmentación automática, puntuación de afinidad (probabilidad de volver),
 * predicción de asistencia, recomendaciones y resumen en lenguaje natural (con IA opcional).
 * Los modelos son reglas y puntuaciones explicables, calculadas con los datos reales de la plataforma.
 */
@Service
@RequiredArgsConstructor
public class InsightsService {

    private static final double SMOOTHING = 10;
    private static final String SYSTEM_PROMPT = "Eres analista de marketing de Coca-Cola. Con los datos que recibes, "
            + "redacta en español un resumen ejecutivo claro (máximo 160 palabras) del evento y termina con tres "
            + "acciones concretas para la próxima activación. No inventes cifras que no estén en los datos.";

    private static final String PREDICTION_PROMPT = "Eres analista de datos de marketing de Coca-Cola. Recibes predicciones calculadas "
            + "por el sistema (asistencia esperada por evento y afinidad de los participantes para volver). Interprétalas en español "
            + "en máximo 170 palabras: qué significan, qué riesgos u oportunidades ves y tres acciones concretas para mejorar la "
            + "asistencia y la recompra. Usa solo las cifras recibidas, sin inventar datos ni mencionar personas.";

    private final com.cocacola.utils.ReadCache cache;
    private final EventRepository events;
    private final ParticipantRepository participants;
    private final InteractionRepository interactions;
    private final SurveyRepository surveys;
    private final MetricsService metrics;
    private final TextGenerator ai;

    // ------------------------------------------------------------------ perfiles

    private record Profile(Participant p, List<Interaction> ints, Survey survey, int eventsAttended) {
        boolean attended() { return p.getCheckedInAt() != null; }
        boolean converted() { return ints.stream().anyMatch(i -> i.getType() == InteractionType.CONVERSION || i.getType() == InteractionType.REDEEM); }
        boolean tasted() { return ints.stream().anyMatch(i -> i.getType() == InteractionType.TASTING); }
        boolean wouldBuy() { return ints.stream().anyMatch(i -> Boolean.TRUE.equals(i.getWouldBuy())); }
        double avgRating() {
            return ints.stream().filter(i -> i.getType() == InteractionType.TASTING && i.getRating() != null)
                    .mapToInt(Interaction::getRating).average().orElse(0);
        }
        String name() { return p.getFirstName() + " " + p.getLastName(); }
    }

    private List<Profile> profiles(String eventId) {
        List<Participant> all = participants.findAll();
        Map<String, Set<String>> eventsByPerson = new LinkedHashMap<>();
        all.stream().filter(p -> p.getCheckedInAt() != null)
                .forEach(p -> eventsByPerson.computeIfAbsent(p.getEmail(), k -> new HashSet<>()).add(p.getEventId()));
        Map<String, List<Interaction>> ints = interactions.findAll().stream().collect(Collectors.groupingBy(Interaction::getParticipantId));
        Map<String, Survey> surveyByParticipant = surveys.findAll().stream().collect(Collectors.toMap(Survey::getParticipantId, s -> s, (a, b) -> a));
        return all.stream()
                .filter(p -> eventId == null || eventId.isBlank() || eventId.equals(p.getEventId()))
                .map(p -> new Profile(p, ints.getOrDefault(p.getId(), List.of()), surveyByParticipant.get(p.getId()),
                        eventsByPerson.getOrDefault(p.getEmail(), Set.of()).size()))
                .toList();
    }

    // ------------------------------------------------------------------ afinidad

    private record Scored(int score, List<String> factors) {
    }

    /** Puntuación 0-100 de probabilidad de volver (regresión logística con pesos fijos y explicables). */
    private Scored score(Profile f) {
        List<String> factors = new ArrayList<>();
        double z = -2.2;
        if (f.attended()) { z += 1.0; factors.add("Asistió al evento"); }
        int n = Math.min(f.ints().size(), 6);
        if (n > 0) { z += 0.30 * n; factors.add(f.ints().size() + " interacciones durante el evento"); }
        if (f.avgRating() > 0) {
            z += 0.35 * (f.avgRating() - 3);
            if (f.avgRating() >= 4) factors.add("Calificó bien los productos (" + String.format("%.1f", f.avgRating()) + "/5)");
        }
        if (f.wouldBuy()) { z += 0.5; factors.add("Compraría el producto"); }
        if (f.p().isConsent()) { z += 0.7; factors.add("Aceptó recibir comunicaciones"); }
        if (f.converted()) { z += 0.6; factors.add("Canjeó o convirtió"); }
        if (f.eventsAttended() > 1) { z += 0.9 * Math.min(f.eventsAttended() - 1, 2); factors.add("Ha asistido a " + f.eventsAttended() + " eventos"); }
        if (f.p().isReturning()) { z += 0.6; factors.add("Participante recurrente"); }
        if (f.survey() != null) {
            z += 0.12 * (f.survey().getNps() - 7);
            if (f.survey().getNps() >= 9) factors.add("Promotor en la encuesta");
        }
        return new Scored((int) Math.round(100 / (1 + Math.exp(-z))), factors);
    }

    private static String level(int score) {
        return score >= 65 ? "Alta" : score >= 35 ? "Media" : "Baja";
    }

    @Transactional(readOnly = true)
    public AffinityReport affinity(String eventId, int limit) {
        return cache.get("i:aff:" + eventId + ":" + limit, () -> computeAffinity(eventId, limit));
    }

    private AffinityReport computeAffinity(String eventId, int limit) {
        List<AffinityScore> all = profiles(eventId).stream().map(f -> {
            Scored s = score(f);
            return new AffinityScore(f.p().getId(), f.name(), f.p().getCity(), f.p().isConsent(), s.score(), level(s.score()), s.factors());
        }).sorted(Comparator.comparingInt(AffinityScore::score).reversed()).toList();
        Map<String, Long> dist = all.stream().collect(Collectors.groupingBy(AffinityScore::level, Collectors.counting()));
        List<NamedValue> distribution = List.of("Alta", "Media", "Baja").stream()
                .map(l -> new NamedValue(l, dist.getOrDefault(l, 0L))).toList();
        return new AffinityReport(distribution, all.stream().mapToInt(AffinityScore::score).average().orElse(0),
                all.stream().limit(Math.max(1, limit)).toList());
    }

    // ------------------------------------------------------------------ segmentación

    @Transactional(readOnly = true)
    public List<Segment> segments(String eventId) {
        return cache.get("i:seg:" + eventId, () -> computeSegments(eventId));
    }

    private List<Segment> computeSegments(String eventId) {
        List<Profile> list = profiles(eventId);
        Map<String, List<Profile>> groups = new LinkedHashMap<>();
        for (String k : List.of("embajadores", "potenciales", "activos", "pasivos", "ausentes")) groups.put(k, new ArrayList<>());
        for (Profile f : list) {
            String key;
            if (!f.attended()) key = "ausentes";
            else if (f.converted() && f.p().isConsent() && (f.p().isReturning() || f.avgRating() >= 4 || f.eventsAttended() > 1)) key = "embajadores";
            else if (!f.converted() && f.tasted() && (f.wouldBuy() || f.avgRating() >= 4)) key = "potenciales";
            else if (!f.ints().isEmpty()) key = "activos";
            else key = "pasivos";
            groups.get(key).add(f);
        }
        String[][] meta = {
                {"embajadores", "Embajadores de marca", "Convirtieron, aceptan comunicaciones y son recurrentes o califican alto.", "Invitarlos primero a los próximos eventos y programa de referidos."},
                {"potenciales", "Clientes potenciales", "Probaron el producto y lo calificaron bien o lo comprarían, pero no convirtieron.", "Enviar un cupón de descuento para activar la primera compra."},
                {"activos", "Participantes activos", "Interactuaron con actividades, pero sin degustación destacada ni conversión.", "Invitar a degustaciones y a más dinámicas en el siguiente evento."},
                {"pasivos", "Asistentes pasivos", "Asistieron, pero no participaron en ninguna actividad.", "Reforzar la invitación a participar en la puerta y con personal en el recorrido."},
                {"ausentes", "Registrados que no asistieron", "Se inscribieron, pero no llegaron.", "Recordatorio previo y mensaje de reactivación para el próximo evento."},
        };
        int total = Math.max(1, list.size());
        List<Segment> out = new ArrayList<>();
        for (String[] m : meta) {
            List<Profile> g = groups.get(m[0]);
            List<SegmentMember> members = g.stream().map(f -> new SegmentMember(f.p().getId(), f.name(), f.p().getCity(),
                            f.p().isConsent(), score(f).score()))
                    .sorted(Comparator.comparingInt(SegmentMember::affinity).reversed()).limit(25).toList();
            out.add(new Segment(m[0], m[1], m[2], m[3], g.size(), g.size() * 100.0 / total, members));
        }
        return out;
    }

    // ------------------------------------------------------------------ predicción de asistencia

    @Transactional(readOnly = true)
    public List<AttendanceForecast> forecast() {
        return cache.get("i:forecast", this::computeForecast);
    }

    private List<AttendanceForecast> computeForecast() {
        List<Event> all = events.findAll();
        List<Event> finished = all.stream().filter(e -> e.getStatus() == EventStatus.FINISHED).toList();
        List<Participant> everyone = participants.findAll();
        Set<String> finishedIds = finished.stream().map(Event::getId).collect(Collectors.toSet());
        List<Participant> history = everyone.stream().filter(p -> finishedIds.contains(p.getEventId())).toList();

        double registeredH = history.size();
        double attendedH = history.stream().filter(p -> p.getCheckedInAt() != null).count();
        Double globalRate = registeredH > 0 ? attendedH / registeredH : null;
        Map<String, double[]> bySourceH = new LinkedHashMap<>();
        for (Participant p : history) {
            double[] v = bySourceH.computeIfAbsent(nz(p.getSource()), k -> new double[2]);
            v[0]++;
            if (p.getCheckedInAt() != null) v[1]++;
        }
        double conversionsH = 0;
        double attendeesH = 0;
        for (Event e : finished) {
            EventMetrics m = metrics.forEvent(e.getId());
            conversionsH += m.conversions();
            attendeesH += m.attended();
        }
        Double conversionRate = attendeesH > 0 ? conversionsH / attendeesH : null;

        List<AttendanceForecast> out = new ArrayList<>();
        for (Event e : all) {
            if (e.getStatus() == EventStatus.FINISHED) continue;
            List<Participant> reg = everyone.stream().filter(p -> e.getId().equals(p.getEventId())).toList();
            int attendedSoFar = (int) reg.stream().filter(p -> p.getCheckedInAt() != null).count();
            if (globalRate == null) {
                out.add(new AttendanceForecast(e.getId(), e.getName(), e.getStatus().getValue(), reg.size(), attendedSoFar, null, null, null,
                        null, null, e.getExpected(), List.of(), "Aún no hay eventos finalizados para calcular la predicción."));
                continue;
            }
            Map<String, int[]> bySource = new LinkedHashMap<>();
            double expected = 0;
            double variance = 0;
            for (Participant p : reg) {
                double[] h = bySourceH.getOrDefault(nz(p.getSource()), new double[2]);
                double rate = (h[1] + SMOOTHING * globalRate) / (h[0] + SMOOTHING);
                if (p.getCheckedInAt() != null) { expected += 1; continue; }   // ya asistió
                expected += rate;
                variance += rate * (1 - rate);
                bySource.computeIfAbsent(nz(p.getSource()), k -> new int[1])[0]++;
            }
            double std = Math.sqrt(variance);
            int exp = (int) Math.round(expected);
            int low = Math.max(attendedSoFar, (int) Math.round(expected - 1.28 * std));
            int high = Math.min(reg.size(), (int) Math.round(expected + 1.28 * std));
            List<SourceForecast> sources = bySource.entrySet().stream().map(en -> {
                double[] h = bySourceH.getOrDefault(en.getKey(), new double[2]);
                double rate = (h[1] + SMOOTHING * globalRate) / (h[0] + SMOOTHING);
                return new SourceForecast(en.getKey(), en.getValue()[0], rate * 100, en.getValue()[0] * rate);
            }).sorted(Comparator.comparingDouble(SourceForecast::rate).reversed()).toList();
            out.add(new AttendanceForecast(e.getId(), e.getName(), e.getStatus().getValue(), reg.size(), attendedSoFar, exp, low, Math.max(low, high),
                    globalRate * 100, conversionRate == null ? null : (int) Math.round(exp * conversionRate), e.getExpected(), sources,
                    "Estimación al 80 % de confianza con la asistencia histórica por fuente de registro."));
        }
        return out;
    }

    // ------------------------------------------------------------------ recomendaciones

    @Transactional(readOnly = true)
    public List<Recommendation> recommendations(String eventId) {
        return cache.get("i:recs:" + eventId, () -> computeRecommendations(eventId));
    }

    private List<Recommendation> computeRecommendations(String eventId) {
        if (eventId == null || eventId.isBlank()) return globalRecommendations();
        EventMetrics m = metrics.forEvent(eventId);
        List<Recommendation> r = new ArrayList<>();

        if (!m.productInterest().isEmpty() && m.registered() > 0) {
            var top = m.productInterest().get(0);
            r.add(new Recommendation("alta", "Producto", "Prioriza " + top.name() + " en el próximo evento",
                    "Es el producto con mayor interés: " + top.value() + " personas (" + fmt(top.pct()) + " % de los registrados)."));
        }
        if (m.registered() > 0 && m.attendanceRate() < 60) {
            r.add(new Recommendation("alta", "Operación", "Mejora la asistencia efectiva",
                    "Solo asistió el " + fmt(m.attendanceRate()) + " % de los registrados. Activa recordatorios 24 h antes y reenvía el QR por WhatsApp o correo."));
        } else if (m.attendanceRate() >= 80) {
            r.add(new Recommendation("baja", "Operación", "Asistencia sobresaliente",
                    "El " + fmt(m.attendanceRate()) + " % de los registrados asistió: replica el canal y la comunicación de este evento."));
        }
        if (m.attended() > 0 && m.participationRate() < 40) {
            r.add(new Recommendation("alta", "Experiencia", "Aumenta la participación en actividades",
                    "Solo el " + fmt(m.participationRate()) + " % de los asistentes participó. Agrega una dinámica atractiva cerca de la entrada y mueve al personal hacia los puntos con poca afluencia."));
        }
        if (m.registered() > 0 && m.consentRate() < 50) {
            r.add(new Recommendation("media", "Marketing", "Incentiva el consentimiento de comunicaciones",
                    "Aceptó recibir información el " + fmt(m.consentRate()) + " %. Ofrece un cupón o beneficio a cambio del consentimiento."));
        }
        if (m.attended() > 0 && m.conversionRate() < 15) {
            r.add(new Recommendation("media", "Marketing", "Revisa el proceso de canje y conversión",
                    "La conversión fue de " + fmt(m.conversionRate()) + " %. Simplifica el canje y entrega el cupón justo después de la degustación."));
        }
        if (!m.hourly().isEmpty()) {
            NamedValue peak = m.hourly().stream().max(Comparator.comparingLong(NamedValue::value)).orElseThrow();
            r.add(new Recommendation("media", "Operación", "Refuerza el personal en la hora pico",
                    "El mayor ingreso fue a las " + peak.name() + " (" + peak.value() + " llegadas). Programa más personal y puntos de ingreso en esa franja."));
        }
        if (m.activityPerformance().size() > 1) {
            NamedValue best = m.activityPerformance().get(0);
            NamedValue worst = m.activityPerformance().get(m.activityPerformance().size() - 1);
            if (best.value() > worst.value()) {
                r.add(new Recommendation("baja", "Experiencia", "Replica \"" + best.name() + "\" y revisa \"" + worst.name() + "\"",
                        best.name() + " atrajo a " + best.value() + " personas; " + worst.name() + " solo a " + worst.value() + "."));
            }
        }
        if (m.surveyCount() > 0) {
            CriterionScore worstCriterion = m.satisfactionByCriterion().stream().filter(c -> c.value() > 0)
                    .min(Comparator.comparingDouble(CriterionScore::value)).orElse(null);
            if (m.nps() < 30) {
                r.add(new Recommendation("alta", "Experiencia", "Eleva el NPS (" + Math.round(m.nps()) + ")",
                        "Pocos asistentes recomendarían la experiencia. Analiza los comentarios y prioriza el criterio con menor puntaje."));
            }
            if (worstCriterion != null && worstCriterion.value() < 4) {
                r.add(new Recommendation("media", "Experiencia", "Mejora: " + criterionLabel(worstCriterion.key()),
                        "Es el criterio con menor calificación (" + String.format("%.1f", worstCriterion.value()) + " / 5)."));
            }
        }
        if (m.attended() > 0 && m.recurrenceIndex() < 20) {
            r.add(new Recommendation("media", "Fidelización", "Construye un programa de fidelización",
                    "Solo el " + fmt(m.recurrenceIndex()) + " % de los asistentes ya había participado antes. Invita a los asistentes de este evento a los próximos."));
        }
        // fuente de registro con mejor asistencia
        List<Participant> reg = participants.findByEventId(eventId);
        Map<String, double[]> bySource = new LinkedHashMap<>();
        for (Participant p : reg) {
            double[] v = bySource.computeIfAbsent(nz(p.getSource()), k -> new double[2]);
            v[0]++;
            if (p.getCheckedInAt() != null) v[1]++;
        }
        bySource.entrySet().stream().filter(en -> en.getValue()[0] >= 5)
                .max(Comparator.comparingDouble(en -> en.getValue()[1] / en.getValue()[0])).ifPresent(en ->
                        r.add(new Recommendation("baja", "Marketing", "Invierte más en \"" + en.getKey() + "\"",
                                "Es la fuente de registro con mejor asistencia (" + fmt(en.getValue()[1] * 100 / en.getValue()[0]) + " %).")));
        r.sort(Comparator.comparingInt(x -> priorityOrder(x.priority())));
        return r;
    }

    private List<Recommendation> globalRecommendations() {
        OverviewMetrics o = metrics.overview();
        List<Recommendation> r = new ArrayList<>();
        o.perEvent().stream().max(Comparator.comparingDouble(e -> e.conversionRate())).ifPresent(best ->
                r.add(new Recommendation("alta", "Producto", "Replica el formato de " + best.name(),
                        "Es el evento con mayor tasa de conversión (" + fmt(best.conversionRate()) + " %).")));
        if (!o.productInterest().isEmpty()) {
            r.add(new Recommendation("alta", "Producto", "Producto con mayor interés global: " + o.productInterest().get(0).name(),
                    "Concentra a " + o.productInterest().get(0).value() + " personas interesadas entre todos los eventos."));
        }
        if (o.recurrenceIndex() < 25) {
            r.add(new Recommendation("media", "Fidelización", "Fortalece la recurrencia",
                    "El índice de recurrencia global es " + fmt(o.recurrenceIndex()) + " %. Invita a los asistentes a nuevos eventos."));
        }
        if (o.attendanceRate() < 65) {
            r.add(new Recommendation("media", "Operación", "Mejora los recordatorios previos",
                    "La asistencia efectiva global es " + fmt(o.attendanceRate()) + " %."));
        }
        return r;
    }

    // ------------------------------------------------------------------ resumen

    @Transactional(readOnly = true)
    public InsightSummary summary(String eventId, boolean useAi) {
        return cache.get("i:sum:" + eventId + ":" + useAi, () -> computeSummary(eventId, useAi));
    }

    private InsightSummary computeSummary(String eventId, boolean useAi) {
        boolean global = eventId == null || eventId.isBlank();
        List<Recommendation> recs = recommendations(global ? null : eventId);
        String title;
        String text;
        if (global) {
            OverviewMetrics o = metrics.overview();
            title = "Resumen de todos los eventos";
            text = "En " + o.events() + " eventos asistieron " + o.attended() + " de " + o.registered() + " personas registradas ("
                    + fmt(o.attendanceRate()) + " %). " + o.productInteractions() + " interactuaron con productos, se generaron "
                    + o.conversions() + " conversiones y " + o.redemptions() + " canjearon un beneficio."
                    + (o.productInterest().isEmpty() ? "" : " El producto con mayor interés fue " + o.productInterest().get(0).name() + ".")
                    + (o.satisfaction() > 0 ? " La satisfacción promedio es " + String.format("%.1f", o.satisfaction()) + " / 5 y el NPS " + Math.round(o.nps()) + "." : "");
        } else {
            EventMetrics m = metrics.forEvent(eventId);
            title = m.event().getName();
            text = "Asistieron " + m.attended() + " de " + m.registered() + " personas registradas (" + fmt(m.attendanceRate()) + " %). "
                    + m.productInteractions() + " interactuaron con productos, se generaron " + m.conversions() + " conversiones y "
                    + m.redemptions() + " canjearon un beneficio."
                    + (m.productInterest().isEmpty() ? "" : " El producto con mayor interés fue " + m.productInterest().get(0).name() + ".")
                    + (m.surveyCount() > 0 ? " La satisfacción fue de " + String.format("%.1f", m.satisfaction()) + " / 5 y el NPS de " + Math.round(m.nps()) + "." : "");
        }
        String aiText = null;
        String source = "local";
        if (useAi && ai.isConfigured()) {
            String prompt = "Evento: " + title + "\nDatos: " + text + "\nRecomendaciones detectadas:\n- "
                    + recs.stream().map(x -> x.title() + ": " + x.detail()).collect(Collectors.joining("\n- "));
            Optional<String> generated = ai.generate(SYSTEM_PROMPT, prompt);
            if (generated.isPresent()) {
                aiText = generated.get();
                source = "ia";
            }
        }
        return new InsightSummary(global ? null : eventId, title, text, recs, aiText, source);
    }

    /** Interpretación de las predicciones (asistencia esperada y afinidad) con IA; sin IA, un texto armado con reglas. */
    @Transactional(readOnly = true)
    public PredictionAnalysis predictionAnalysis(String eventId) {
        return cache.get("i:pred:" + eventId, () -> computePredictionAnalysis(eventId));
    }

    private PredictionAnalysis computePredictionAnalysis(String eventId) {
        List<AttendanceForecast> forecasts = forecast().stream()
                .filter(f -> eventId == null || eventId.isBlank() || f.eventId().equals(eventId)).toList();
        AffinityReport affinity = affinity(eventId, 1);
        StringBuilder data = new StringBuilder();
        for (AttendanceForecast f : forecasts) {
            data.append("Evento ").append(f.eventName()).append(": ");
            if (f.expectedAttendees() == null) {
                data.append(f.note()).append('\n');
                continue;
            }
            data.append("asistentes esperados ").append(f.expectedAttendees()).append(" (rango ").append(f.low()).append(" a ").append(f.high())
                    .append("), registrados ").append(f.registered()).append(", ya ingresaron ").append(f.attendedSoFar());
            if (f.targetExpected() != null && f.targetExpected() > 0) data.append(", meta ").append(f.targetExpected());
            if (f.historicalRate() != null) data.append(", asistencia histórica ").append(fmt(f.historicalRate())).append(" %");
            if (f.projectedConversions() != null) data.append(", conversiones proyectadas ").append(f.projectedConversions());
            data.append('\n');
        }
        data.append("Afinidad promedio para volver: ").append(fmt(affinity.averageScore())).append(" / 100. Distribución: ")
                .append(affinity.distribution().stream().map(d -> d.name() + " " + (int) d.value()).collect(Collectors.joining(", "))).append('.');
        if (ai.isConfigured()) {
            Optional<String> generated = ai.generate(PREDICTION_PROMPT, data.toString());
            if (generated.isPresent()) return new PredictionAnalysis(generated.get(), "ia");
        }
        return new PredictionAnalysis(data.toString().replace("\n", " "), "local");
    }

    public boolean aiConfigured() {
        return ai.isConfigured();
    }

    // ------------------------------------------------------------------ utilidades

    private static String nz(String s) {
        return s == null || s.isBlank() ? "Sin dato" : s;
    }

    private static String fmt(double v) {
        return String.format("%.0f", v);
    }

    private static int priorityOrder(String p) {
        return switch (p) {
            case "alta" -> 0;
            case "media" -> 1;
            default -> 2;
        };
    }

    private static String criterionLabel(String key) {
        return switch (key) {
            case "organization" -> "organización";
            case "service" -> "atención";
            case "experiences" -> "experiencias y dinámicas";
            case "products" -> "productos probados";
            default -> "experiencia general";
        };
    }
}
