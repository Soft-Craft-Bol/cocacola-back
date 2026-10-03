package com.cocacola.domain.service;

import com.cocacola.commons.Constants;
import com.cocacola.commons.enums.InteractionType;
import com.cocacola.domain.helpers.NotFoundException;
import com.cocacola.domain.model.Activity;
import com.cocacola.domain.model.Event;
import com.cocacola.domain.model.Interaction;
import com.cocacola.domain.model.Participant;
import com.cocacola.domain.model.Product;
import com.cocacola.domain.model.Survey;
import com.cocacola.domain.model.metrics.CriterionScore;
import com.cocacola.domain.model.metrics.EventMetrics;
import com.cocacola.domain.model.metrics.EventSummary;
import com.cocacola.domain.model.metrics.EvolutionPoint;
import com.cocacola.domain.model.metrics.NamedValue;
import com.cocacola.domain.model.metrics.OverviewMetrics;
import com.cocacola.domain.model.metrics.ProductInterest;
import com.cocacola.domain.repository.ActivityRepository;
import com.cocacola.domain.repository.EventRepository;
import com.cocacola.domain.repository.InteractionRepository;
import com.cocacola.domain.repository.ParticipantRepository;
import com.cocacola.domain.repository.ProductRepository;
import com.cocacola.domain.repository.SurveyRepository;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final EventRepository events;
    private final ParticipantRepository participants;
    private final InteractionRepository interactions;
    private final SurveyRepository surveys;
    private final ActivityRepository activities;
    private final ProductRepository products;

    @Value("${app.timezone:America/Bogota}")
    private String timezone;

    @Transactional(readOnly = true)
    public EventMetrics forEvent(String eventId) {
        Event event = events.findById(eventId).orElseThrow(() -> new NotFoundException("Evento"));
        return compute(event, participants.findByEventId(eventId), interactions.findByEventId(eventId),
                surveys.findByEventId(eventId), activities.findByEventId(eventId), productNames());
    }

    @Transactional(readOnly = true)
    public OverviewMetrics overview() {
        List<Event> allEvents = events.findAll();
        List<Participant> allParticipants = participants.findAll();
        List<Interaction> allInteractions = interactions.findAll();
        List<Survey> allSurveys = surveys.findAll();
        List<Activity> allActivities = activities.findAll();
        Map<String, String> names = productNames();

        var partsByEvent = allParticipants.stream().collect(Collectors.groupingBy(Participant::getEventId));
        var intsByEvent = allInteractions.stream().collect(Collectors.groupingBy(Interaction::getEventId));
        var surveysByEvent = allSurveys.stream().collect(Collectors.groupingBy(Survey::getEventId));
        var actsByEvent = allActivities.stream().collect(Collectors.groupingBy(Activity::getEventId));
        List<EventMetrics> per = allEvents.stream().map(e -> compute(e,
                partsByEvent.getOrDefault(e.getId(), List.of()),
                intsByEvent.getOrDefault(e.getId(), List.of()),
                surveysByEvent.getOrDefault(e.getId(), List.of()),
                actsByEvent.getOrDefault(e.getId(), List.of()), names)).toList();

        int attended = sum(per, EventMetrics::attended);
        int registered = sum(per, EventMetrics::registered);
        int returning = sum(per, EventMetrics::returningCount);

        Map<String, Set<String>> eventsPerPerson = new HashMap<>();
        allParticipants.forEach(p -> eventsPerPerson.computeIfAbsent(p.getEmail(), k -> new HashSet<>()).add(p.getEventId()));
        Map<Integer, Long> freq = new TreeMap<>();
        eventsPerPerson.values().forEach(s -> freq.merge(s.size(), 1L, Long::sum));
        List<NamedValue> loyalty = freq.entrySet().stream()
                .map(en -> new NamedValue(en.getKey() + (en.getKey() > 1 ? " eventos" : " evento"), en.getValue())).toList();

        Map<String, ProductInterest> merged = new LinkedHashMap<>();
        per.stream().flatMap(m -> m.productInterest().stream()).forEach(pi -> merged.merge(pi.productId(), pi,
                (a, b) -> new ProductInterest(a.productId(), a.name(), a.value() + b.value(), 0)));
        List<ProductInterest> productInterest = merged.values().stream()
                .map(pi -> new ProductInterest(pi.productId(), pi.name(), pi.value(), pct(pi.value(), allParticipants.size())))
                .sorted(Comparator.comparingLong(ProductInterest::value).reversed()).toList();

        List<EventSummary> perEvent = per.stream().map(m -> new EventSummary(m.event().getId(), m.event().getName(),
                m.event().getType(), m.event().getCampaign(), m.event().getDate(), m.registered(), m.attended(),
                m.attendanceRate(), m.productInteractions(), m.conversions(), m.redemptions(), m.satisfaction(), m.nps(),
                m.participationRate(), m.conversionRate())).toList();

        List<EvolutionPoint> evolution = per.stream()
                .sorted(Comparator.comparing(m -> m.event().getDate()))
                .map(m -> new EvolutionPoint(m.event().getName(), m.event().getDate(), m.attended(), m.returningCount())).toList();

        return new OverviewMetrics(
                allEvents.size(), registered, attended, pct(attended, registered),
                sum(per, EventMetrics::productInteractions), sum(per, EventMetrics::samples),
                sum(per, EventMetrics::conversions), sum(per, EventMetrics::redemptions), sum(per, EventMetrics::consents),
                returning, pct(returning, attended),
                per.stream().filter(m -> m.satisfaction() > 0).mapToDouble(EventMetrics::satisfaction).average().orElse(0),
                nps(allSurveys), perEvent,
                countBy(allParticipants, Participant::getCity), countBy(allParticipants, Participant::getAgeRange),
                countBy(allParticipants, Participant::getSource), countBy(allParticipants, Participant::getCampaign),
                List.of(new NamedValue("Con consentimiento", allParticipants.stream().filter(Participant::isConsent).count()),
                        new NamedValue("Sin consentimiento", allParticipants.stream().filter(p -> !p.isConsent()).count())),
                List.of(new NamedValue("Nuevos", sum(per, EventMetrics::newCount)), new NamedValue("Recurrentes", returning)),
                loyalty, productInterest, evolution);
    }

    // ---------------------------------------------------------------- calculo por evento

    private EventMetrics compute(Event event, List<Participant> parts, List<Interaction> ints, List<Survey> surv,
                                 List<Activity> acts, Map<String, String> productNames) {
        List<Participant> attendees = parts.stream().filter(p -> p.getCheckedInAt() != null).toList();
        Set<String> attendeeIds = attendees.stream().map(Participant::getId).collect(Collectors.toSet());

        List<Interaction> tastings = ofType(ints, InteractionType.TASTING);
        List<Interaction> redemptions = ofType(ints, InteractionType.REDEEM);
        List<Interaction> conversions = ofType(ints, InteractionType.CONVERSION);

        Set<String> participated = idsOf(ints.stream().filter(i -> EnumSet.of(InteractionType.ACTIVITY,
                InteractionType.TASTING, InteractionType.REDEEM).contains(i.getType())).toList());
        Set<String> productInteractors = idsOf(concat(tastings, redemptions));
        Set<String> converted = idsOf(concat(conversions, redemptions));

        int newCount = (int) attendees.stream().filter(p -> !p.isReturning()).count();
        int returningCount = attendees.size() - newCount;
        int consents = (int) parts.stream().filter(Participant::isConsent).count();

        Map<String, Set<String>> interest = new LinkedHashMap<>();
        parts.forEach(p -> {
            if (p.getPreferences() != null) {
                p.getPreferences().forEach(id -> interest.computeIfAbsent(id, k -> new HashSet<>()).add(p.getId()));
            }
        });
        tastings.stream().filter(t -> t.getProductId() != null)
                .forEach(t -> interest.computeIfAbsent(t.getProductId(), k -> new HashSet<>()).add(t.getParticipantId()));
        List<ProductInterest> productInterest = interest.entrySet().stream()
                .map(en -> new ProductInterest(en.getKey(), productNames.getOrDefault(en.getKey(), en.getKey()),
                        en.getValue().size(), pct(en.getValue().size(), parts.size())))
                .sorted(Comparator.comparingLong(ProductInterest::value).reversed()).toList();

        Map<String, Set<String>> activityParticipants = new HashMap<>();
        ints.stream().filter(i -> i.getActivityId() != null).forEach(i -> activityParticipants
                .computeIfAbsent(i.getActivityId(), k -> new HashSet<>()).add(i.getParticipantId()));
        List<NamedValue> activityPerformance = acts.stream()
                .map(a -> new NamedValue(a.getName(), activityParticipants.getOrDefault(a.getId(), Set.of()).size()))
                .sorted(Comparator.comparingLong(NamedValue::value).reversed()).toList();

        ZoneId zone = ZoneId.of(timezone);
        Map<Integer, Long> byHour = new TreeMap<>(attendees.stream().collect(Collectors.groupingBy(
                p -> p.getCheckedInAt().atZone(zone).getHour(), Collectors.counting())));
        List<NamedValue> hourly = byHour.entrySet().stream()
                .map(en -> new NamedValue(String.format("%02d:00", en.getKey()), en.getValue())).toList();

        double avgStay = attendees.stream().filter(p -> p.getCheckedOutAt() != null)
                .mapToDouble(p -> Duration.between(p.getCheckedInAt(), p.getCheckedOutAt()).toSeconds() / 60.0)
                .average().orElse(0);

        List<CriterionScore> byCriterion = List.of(
                criterion("organization", surv, Survey::getOrganization), criterion("service", surv, Survey::getService),
                criterion("experiences", surv, Survey::getExperiences), criterion("products", surv, Survey::getProducts),
                criterion("overall", surv, Survey::getOverall));
        double satisfaction = byCriterion.stream().filter(c -> c.value() > 0).mapToDouble(CriterionScore::value).average().orElse(0);

        int participatedAttendees = countIn(participated, attendeeIds);
        int convertedAttendees = countIn(converted, attendeeIds);
        List<NamedValue> funnel = List.of(
                new NamedValue("Registrados", parts.size()),
                new NamedValue("Asistieron", attendees.size()),
                new NamedValue("Participaron", participatedAttendees),
                new NamedValue("Probaron / canjearon", countIn(productInteractors, attendeeIds)),
                new NamedValue("Aceptaron comunicaciones", attendees.stream().filter(Participant::isConsent).count()),
                new NamedValue("Convirtieron", convertedAttendees));

        return new EventMetrics(event, parts.size(), attendees.size(), pct(attendees.size(), parts.size()),
                newCount, returningCount, pct(returningCount, attendees.size()),
                productInteractors.size(), tastings.size(), pct(participatedAttendees, attendees.size()),
                consents, pct(consents, parts.size()), converted.size(), pct(convertedAttendees, attendees.size()),
                redemptions.size(), attendees.isEmpty() ? 0 : (double) ints.size() / attendees.size(), avgStay,
                satisfaction, byCriterion, nps(surv), surv.size(), productInterest, activityPerformance, hourly, funnel,
                countBy(parts, Participant::getCity), countBy(parts, Participant::getAgeRange),
                countBy(parts, Participant::getSource), countBy(parts, Participant::getCampaign));
    }

    // ---------------------------------------------------------------- helpers

    private Map<String, String> productNames() {
        return products.findAll().stream().collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));
    }

    private static List<Interaction> ofType(List<Interaction> list, InteractionType type) {
        return list.stream().filter(i -> i.getType() == type).toList();
    }

    private static List<Interaction> concat(List<Interaction> a, List<Interaction> b) {
        return java.util.stream.Stream.concat(a.stream(), b.stream()).toList();
    }

    private static Set<String> idsOf(List<Interaction> list) {
        return list.stream().map(Interaction::getParticipantId).collect(Collectors.toSet());
    }

    private static int countIn(Set<String> ids, Set<String> attendeeIds) {
        return (int) ids.stream().filter(attendeeIds::contains).count();
    }

    private static CriterionScore criterion(String key, List<Survey> surveys, ToIntFunction<Survey> fn) {
        return new CriterionScore(key, surveys.stream().mapToInt(fn).average().orElse(0));
    }

    private static <T> int sum(List<T> list, ToIntFunction<T> fn) {
        return list.stream().mapToInt(fn).sum();
    }

    private static double pct(double a, double b) {
        return b == 0 ? 0 : a / b * 100;
    }

    private static double nps(List<Survey> surveys) {
        if (surveys.isEmpty()) return 0;
        long promoters = surveys.stream().filter(s -> s.getNps() >= Constants.NPS_PROMOTER_MIN).count();
        long detractors = surveys.stream().filter(s -> s.getNps() <= Constants.NPS_DETRACTOR_MAX).count();
        return (promoters - detractors) * 100.0 / surveys.size();
    }

    private static <T> List<NamedValue> countBy(List<T> list, Function<T, String> fn) {
        Map<String, Long> counts = list.stream().collect(Collectors.groupingBy(
                x -> fn.apply(x) == null ? "Sin dato" : fn.apply(x), LinkedHashMap::new, Collectors.counting()));
        return counts.entrySet().stream().map(en -> new NamedValue(en.getKey(), en.getValue())).toList();
    }
}
