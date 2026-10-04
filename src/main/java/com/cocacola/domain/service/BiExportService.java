package com.cocacola.domain.service;

import com.cocacola.commons.enums.InteractionType;
import com.cocacola.domain.helpers.Hashing;
import com.cocacola.domain.helpers.NotFoundException;
import com.cocacola.domain.model.Activity;
import com.cocacola.domain.model.Event;
import com.cocacola.domain.model.Interaction;
import com.cocacola.domain.model.Participant;
import com.cocacola.domain.model.Product;
import com.cocacola.domain.model.Survey;
import com.cocacola.domain.model.metrics.EventMetrics;
import com.cocacola.domain.repository.ActivityRepository;
import com.cocacola.domain.repository.EventRepository;
import com.cocacola.domain.repository.InteractionRepository;
import com.cocacola.domain.repository.ParticipantRepository;
import com.cocacola.domain.repository.ProductRepository;
import com.cocacola.domain.repository.SurveyRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tablas planas listas para Power BI (modelo en estrella: dimensiones eventos/productos/actividades/participantes
 * y hechos interacciones/encuestas). No incluyen datos personales: la persona se identifica con un hash del correo.
 */
@Service
@RequiredArgsConstructor
public class BiExportService {

    public record Column(String name, String type) {
    }

    public record TableInfo(String name, String description, List<Column> columns) {
        public List<String> columnNames() {
            return columns.stream().map(Column::name).toList();
        }
    }

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final List<String> LEVELS = List.of("Visitante", "Registrado", "Participó en una actividad",
            "Probó un producto o canjeó un beneficio", "Aceptó recibir información", "Conversión relevante");

    private static Column text(String n) { return new Column(n, "text"); }
    private static Column integer(String n) { return new Column(n, "integer"); }
    private static Column number(String n) { return new Column(n, "number"); }
    private static Column date(String n) { return new Column(n, "date"); }
    private static Column dateTime(String n) { return new Column(n, "datetime"); }

    private static final List<TableInfo> CATALOG = List.of(
            new TableInfo("eventos", "Dimensión: un registro por evento", List.of(
                    text("evento_id"), text("nombre"), text("tipo"), date("fecha"), text("lugar"), text("organizador"),
                    text("responsable"), text("campana"), number("presupuesto"), integer("esperados"), text("canal"),
                    text("estado"), text("productos_destacados"), text("experiencias_destacadas"))),
            new TableInfo("productos", "Dimensión: catálogo de productos", List.of(
                    text("producto_id"), text("producto"), text("categoria"), text("sabor"), text("presentacion"), text("archivado"))),
            new TableInfo("experiencias", "Dimensión: catálogo de experiencias destacadas", List.of(
                    text("experiencia_id"), text("experiencia"), text("categoria"), text("descripcion"), text("archivada"))),
            new TableInfo("actividades", "Dimensión: actividades de cada evento", List.of(
                    text("actividad_id"), text("evento_id"), text("actividad"), text("tipo_actividad"), text("experiencia_id"), text("experiencia"))),
            new TableInfo("participantes", "Dimensión: un registro por inscripción (sin datos personales)", List.of(
                    text("participante_id"), text("evento_id"), text("persona_id"), text("ciudad"), text("rango_edad"),
                    text("tipo_participante"), text("consentimiento"), text("fuente_registro"), text("campana"),
                    dateTime("fecha_registro"), dateTime("fecha_ingreso"), dateTime("fecha_salida"), text("asistio"),
                    number("permanencia_min"), integer("nivel_interaccion"), text("nivel_nombre"), text("productos_interes"))),
            new TableInfo("interacciones", "Hechos: participaciones, degustaciones, canjes y conversiones", List.of(
                    text("interaccion_id"), text("evento_id"), text("participante_id"), text("actividad_id"),
                    text("actividad"), text("tipo"), text("producto_id"), text("producto"), integer("calificacion"),
                    text("compraria"), text("quiere_promociones"), dateTime("fecha_hora"), date("fecha"), integer("hora"),
                    text("categoria"), text("sabor"), text("presentacion"))),
            new TableInfo("encuestas", "Hechos: satisfacción (1 a 5) y NPS (0 a 10)", List.of(
                    text("encuesta_id"), text("evento_id"), text("participante_id"), integer("organizacion"),
                    integer("atencion"), integer("experiencias"), integer("productos"), integer("general"),
                    number("satisfaccion_promedio"), integer("nps"), text("categoria_nps"), dateTime("fecha"))),
            new TableInfo("indicadores_evento", "Resumen: indicadores calculados por evento", List.of(
                    text("evento_id"), text("evento"), integer("registrados"), integer("asistentes"), number("asistencia_pct"),
                    integer("nuevos"), integer("recurrentes"), number("indice_recurrencia_pct"), integer("interacciones_producto"),
                    integer("muestras"), number("tasa_participacion_pct"), integer("consentimientos"), integer("conversiones"),
                    number("tasa_conversion_pct"), integer("canjes"), number("satisfaccion"), number("nps"), number("permanencia_min"))));

    private final EventRepository events;
    private final ParticipantRepository participants;
    private final InteractionRepository interactions;
    private final SurveyRepository surveys;
    private final ActivityRepository activities;
    private final ProductRepository products;
    private final com.cocacola.persistence.crud.ExperienceRepository experiences;
    private final MetricsService metrics;

    @Value("${app.timezone:America/Bogota}")
    private String timezone;

    public List<TableInfo> catalog() {
        return CATALOG;
    }

    public TableInfo table(String name) {
        return CATALOG.stream().filter(t -> t.name().equalsIgnoreCase(name)).findFirst()
                .orElseThrow(() -> new NotFoundException("Tabla"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> rows(String name) {
        return switch (table(name).name()) {
            case "eventos" -> eventos();
            case "productos" -> productos();
            case "experiencias" -> experiencias();
            case "actividades" -> actividades();
            case "participantes" -> participantes();
            case "interacciones" -> interacciones();
            case "encuestas" -> encuestas();
            default -> indicadores();
        };
    }

    // ------------------------------------------------------------------ tablas

    private List<Map<String, Object>> eventos() {
        Map<String, String> names = productNames();
        Map<String, String> expNames = experienceNames();
        return events.findAll().stream().map(e -> row(
                "evento_id", e.getId(), "nombre", e.getName(), "tipo", e.getType(), "fecha", day(e.getDate()),
                "lugar", e.getLocation(), "organizador", e.getOrganizer(), "responsable", e.getManager(),
                "campana", e.getCampaign(), "presupuesto", e.getBudget(), "esperados", e.getExpected(),
                "canal", e.getChannel(), "estado", status(e),
                "productos_destacados", e.getProductIds() == null ? "" : e.getProductIds().stream()
                        .map(id -> names.getOrDefault(id, id)).collect(Collectors.joining(", ")),
                "experiencias_destacadas", e.getExperienceIds() == null ? "" : e.getExperienceIds().stream()
                        .map(id -> expNames.getOrDefault(id, id)).collect(Collectors.joining(", ")))).toList();
    }

    private List<Map<String, Object>> experiencias() {
        return experiences.findAll().stream().sorted(java.util.Comparator.comparing(e -> e.getName() == null ? "" : e.getName()))
                .map(e -> row("experiencia_id", e.getId(), "experiencia", e.getName(), "categoria", e.getCategory(),
                        "descripcion", e.getDescription(), "archivada", yesNo(e.getArchived()))).toList();
    }

    private List<Map<String, Object>> productos() {
        return products.findAll().stream()
                .map(p -> row("producto_id", p.getId(), "producto", p.displayName(), "categoria", p.getCategory(),
                        "sabor", p.getFlavor(), "presentacion", p.getPresentation(), "archivado", yesNo(p.getArchived()))).toList();
    }

    private List<Map<String, Object>> actividades() {
        Map<String, String> expNames = experienceNames();
        return activities.findAll().stream().map(a -> row("actividad_id", a.getId(), "evento_id", a.getEventId(),
                "actividad", a.getName(), "tipo_actividad", activityType(a), "experiencia_id", a.getExperienceId(),
                "experiencia", a.getExperienceId() == null ? "" : expNames.getOrDefault(a.getExperienceId(), a.getExperienceId()))).toList();
    }

    private List<Map<String, Object>> participantes() {
        Map<String, String> names = productNames();
        Map<String, List<Interaction>> byParticipant = interactions.findAll().stream()
                .collect(Collectors.groupingBy(Interaction::getParticipantId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Participant p : participants.findAll()) {
            int level = level(p, byParticipant.getOrDefault(p.getId(), List.of()));
            Double stay = p.getCheckedInAt() != null && p.getCheckedOutAt() != null
                    ? Duration.between(p.getCheckedInAt(), p.getCheckedOutAt()).toSeconds() / 60.0 : null;
            out.add(row("participante_id", p.getId(), "evento_id", p.getEventId(), "persona_id", Hashing.personId(p.getEmail()),
                    "ciudad", p.getCity(), "rango_edad", p.getAgeRange(),
                    "tipo_participante", p.isReturning() ? "Recurrente" : "Nuevo",
                    "consentimiento", yesNo(p.isConsent()), "fuente_registro", p.getSource(), "campana", p.getCampaign(),
                    "fecha_registro", stamp(p.getRegisteredAt()), "fecha_ingreso", stamp(p.getCheckedInAt()),
                    "fecha_salida", stamp(p.getCheckedOutAt()), "asistio", yesNo(p.getCheckedInAt() != null),
                    "permanencia_min", stay == null ? null : Math.round(stay * 10) / 10.0,
                    "nivel_interaccion", level, "nivel_nombre", LEVELS.get(level - 1),
                    "productos_interes", p.getPreferences() == null ? "" : p.getPreferences().stream()
                            .map(id -> names.getOrDefault(id, id)).collect(Collectors.joining(", "))));
        }
        return out;
    }

    private List<Map<String, Object>> interacciones() {
        Map<String, String> names = productNames();
        Map<String, String> acts = activities.findAll().stream().collect(Collectors.toMap(Activity::getId, Activity::getName, (a, b) -> a));
        Map<String, Product> productMap = products.findAll().stream().collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        ZoneId zone = ZoneId.of(timezone);
        return interactions.findAll().stream().map(i -> row(
                "interaccion_id", i.getId(), "evento_id", i.getEventId(), "participante_id", i.getParticipantId(),
                "actividad_id", i.getActivityId(), "actividad", acts.getOrDefault(i.getActivityId(), ""),
                "tipo", interactionType(i.getType()), "producto_id", i.getProductId(),
                "producto", i.getProductId() == null ? "" : names.getOrDefault(i.getProductId(), i.getProductId()),
                "calificacion", i.getRating(), "compraria", yesNo(i.getWouldBuy()), "quiere_promociones", yesNo(i.getWantsPromos()),
                "fecha_hora", stamp(i.getAt()), "fecha", day(i.getAt()),
                "hora", i.getAt() == null ? null : i.getAt().atZone(zone).getHour(),
                "categoria", dim(productMap.get(i.getProductId()), Product::getCategory),
                "sabor", dim(productMap.get(i.getProductId()), Product::getFlavor),
                "presentacion", dim(productMap.get(i.getProductId()), Product::getPresentation))).toList();
    }

    private List<Map<String, Object>> encuestas() {
        return surveys.findAll().stream().map(s -> row(
                "encuesta_id", s.getId(), "evento_id", s.getEventId(), "participante_id", s.getParticipantId(),
                "organizacion", s.getOrganization(), "atencion", s.getService(), "experiencias", s.getExperiences(),
                "productos", s.getProducts(), "general", s.getOverall(),
                "satisfaccion_promedio", Math.round((s.getOrganization() + s.getService() + s.getExperiences()
                        + s.getProducts() + s.getOverall()) / 5.0 * 100) / 100.0,
                "nps", s.getNps(), "categoria_nps", npsCategory(s), "fecha", stamp(s.getCreatedAt()))).toList();
    }

    private List<Map<String, Object>> indicadores() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Event e : events.findAll()) {
            EventMetrics m = metrics.forEvent(e.getId());
            out.add(row("evento_id", e.getId(), "evento", e.getName(), "registrados", m.registered(), "asistentes", m.attended(),
                    "asistencia_pct", round(m.attendanceRate()), "nuevos", m.newCount(), "recurrentes", m.returningCount(),
                    "indice_recurrencia_pct", round(m.recurrenceIndex()), "interacciones_producto", m.productInteractions(),
                    "muestras", m.samples(), "tasa_participacion_pct", round(m.participationRate()), "consentimientos", m.consents(),
                    "conversiones", m.conversions(), "tasa_conversion_pct", round(m.conversionRate()), "canjes", m.redemptions(),
                    "satisfaccion", round(m.satisfaction()), "nps", round(m.nps()), "permanencia_min", round(m.avgStayMinutes())));
        }
        return out;
    }

    // ------------------------------------------------------------------ utilidades

    /** Nivel de interacción (1-6) según el reto: registrado, participó, probó/canjeó, aceptó información, conversión. */
    private int level(Participant p, List<Interaction> list) {
        Set<InteractionType> types = list.stream().map(Interaction::getType).collect(Collectors.toSet());
        int level = 2;
        if (types.contains(InteractionType.ACTIVITY)) level = 3;
        if (types.contains(InteractionType.TASTING) || types.contains(InteractionType.REDEEM)) level = 4;
        if (p.isConsent() && p.getCheckedInAt() != null && level >= 3) level = 5;
        if (types.contains(InteractionType.CONVERSION)) level = 6;
        return level;
    }

    private Map<String, String> productNames() {
        return products.findAll().stream().collect(Collectors.toMap(Product::getId, Product::displayName, (a, b) -> a));
    }

    private Map<String, String> experienceNames() {
        return experiences.findAll().stream().collect(Collectors.toMap(e -> e.getId(), e -> e.getName(), (a, b) -> a));
    }

    private static String dim(Product p, java.util.function.Function<Product, String> f) {
        return p == null || f.apply(p) == null ? "" : f.apply(p);
    }

    private String stamp(Instant t) {
        return t == null ? null : DATE_TIME.format(t.atZone(ZoneId.of(timezone)));
    }

    private String day(Instant t) {
        return t == null ? null : DATE.format(t.atZone(ZoneId.of(timezone)));
    }

    private static String yesNo(Boolean b) {
        return b == null ? "" : b ? "Sí" : "No";
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }

    private static String status(Event e) {
        return e.getStatus() == null ? "" : switch (e.getStatus()) {
            case PLANNED -> "Planificado";
            case ACTIVE -> "En curso";
            case FINISHED -> "Finalizado";
        };
    }

    private static String activityType(Activity a) {
        return switch (a.getType()) {
            case TASTING -> "Degustación o muestra";
            case EXPERIENCE -> "Experiencia de producto";
            case CONTEST -> "Concurso o dinámica";
            case PROMO -> "Activación promocional";
            case PHOTOCALL -> "Fotografía / experiencia digital";
            case SURVEY -> "Encuesta";
            case REDEEM -> "Canje de beneficio o cupón";
            case CONTENT -> "Interacción con contenido de marca";
        };
    }

    private static String interactionType(InteractionType t) {
        return switch (t) {
            case ACTIVITY -> "Participación";
            case TASTING -> "Degustación";
            case REDEEM -> "Canje";
            case CONVERSION -> "Conversión";
        };
    }

    private static String npsCategory(Survey s) {
        return s.getNps() >= 9 ? "Promotor" : s.getNps() >= 7 ? "Pasivo" : "Detractor";
    }

    /** Construye una fila respetando el orden de los pares clave/valor. */
    private static Map<String, Object> row(Object... pairs) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) m.put((String) pairs[i], pairs[i + 1]);
        return m;
    }
}
