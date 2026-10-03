package com.cocacola.config;

import com.cocacola.commons.enums.ActivityType;
import com.cocacola.commons.enums.EventStatus;
import com.cocacola.commons.enums.InteractionType;
import com.cocacola.domain.model.Activity;
import com.cocacola.domain.model.Event;
import com.cocacola.domain.model.Interaction;
import com.cocacola.domain.model.Participant;
import com.cocacola.domain.model.Survey;
import com.cocacola.domain.repository.ActivityRepository;
import com.cocacola.domain.repository.EventRepository;
import com.cocacola.domain.repository.InteractionRepository;
import com.cocacola.domain.repository.ParticipantRepository;
import com.cocacola.domain.repository.SurveyRepository;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Datos de demostracion (3 eventos con participantes, interacciones y encuestas) para que el dashboard
 * tenga informacion desde el primer arranque. Solo corre si no existe ningun evento.
 * Desactivar con app.seed.demo=false.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.demo", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final List<String> NAMES = List.of("Camila", "Andrés", "Valentina", "Juan", "Sofía", "Santiago",
            "Daniela", "Mateo", "Laura", "Sebastián", "Isabella", "Felipe", "Mariana", "Nicolás", "Paula", "David",
            "Natalia", "Carlos", "Juliana", "Esteban");
    private static final List<String> LAST_NAMES = List.of("Gómez", "Rodríguez", "Martínez", "López", "Hernández",
            "García", "Pérez", "Ramírez", "Torres", "Vargas", "Castro", "Rojas", "Moreno", "Jiménez", "Ortiz");
    private static final List<String> CITIES = List.of("Bogotá", "Medellín", "Cali", "Barranquilla", "Cartagena", "Bucaramanga");
    private static final List<String> AGES = List.of("18-24", "25-34", "35-44", "45-54", "55+");
    private static final List<String> SOURCES = List.of("Código QR", "Formulario web", "Tablet en sitio", "Aplicación móvil", "Preinscripción web");

    private record Person(String firstName, String lastName, String email, String phone, String city, String ageRange) {
    }

    private final EventRepository events;
    private final ActivityRepository activities;
    private final ParticipantRepository participants;
    private final InteractionRepository interactions;
    private final SurveyRepository surveys;

    @Value("${app.timezone:America/Bogota}")
    private String timezone;

    @Override
    public void run(ApplicationArguments args) {
        if (!events.findAll().isEmpty()) {
            renameLegacyDemoNames();
            return;
        }

        Random rnd = new Random(2026);
        Instant now = Instant.now();

        List<Event> demoEvents = List.of(
                event("e1", "Coca-Cola Experience 2026", "Festival o concierto", -60, "Parque Norte, Medellín",
                        "Oscar Organizador", "Ana Administradora", "Festival de música con zonas de degustación y cabina de fotos.",
                        "Generar 100 registros con consentimiento", "Destapa la felicidad", 45_000_000L, 100,
                        "Producción propia", List.of("p1", "p2", "p3", "p5"), EventStatus.FINISHED),
                event("e2", "Activación Deportiva Powerade", "Evento deportivo", -25, "Unidad Deportiva Belmonte, Medellín",
                        "Oscar Organizador", "Mónica Marketing", "Carrera 5K con estación de hidratación y entrega de muestras.",
                        "Aumentar prueba de Powerade", "Verano Zero", 22_000_000L, 70, "Aliado deportivo",
                        List.of("p6", "p7", "p3"), EventStatus.FINISHED),
                event("e3", "Muestras Sabores del Barrio", "Campaña promocional / punto de venta", 2,
                        "Centro Comercial Paseo Aranjuez", "Oscar Organizador", "Mónica Marketing",
                        "Activación en punto de venta con cupones de descuento.", "Canjear 40 cupones",
                        "Sabores del barrio", 12_000_000L, 60, "Distribuidor", List.of("p1", "p4", "p5"), EventStatus.ACTIVE));
        demoEvents.forEach(events::save);

        List<Activity> demoActivities = List.of(
                act("a1", "e1", "Zona de degustación", ActivityType.TASTING), act("a2", "e1", "Cabina de fotos Coca-Cola", ActivityType.PHOTOCALL),
                act("a3", "e1", "Concurso de karaoke", ActivityType.CONTEST), act("a4", "e1", "Canje de cupón", ActivityType.REDEEM),
                act("a5", "e2", "Estación de hidratación", ActivityType.TASTING), act("a6", "e2", "Reto 5K", ActivityType.CONTEST),
                act("a7", "e2", "Canje de beneficio", ActivityType.REDEEM), act("a8", "e3", "Mesa de muestras", ActivityType.TASTING),
                act("a9", "e3", "Ruleta de premios", ActivityType.CONTEST), act("a10", "e3", "Canje de cupón", ActivityType.REDEEM));
        demoActivities.forEach(activities::save);

        List<Person> people = IntStream.range(0, 110).mapToObj(i -> {
            String first = pick(rnd, NAMES);
            String last = pick(rnd, LAST_NAMES);
            return new Person(first, last, plain(first + "." + last + i + "@mail.com").toLowerCase(),
                    "3" + (100_000_000 + rnd.nextInt(899_999_999)), pick(rnd, CITIES), pick(rnd, AGES));
        }).toList();

        List<Participant> allParticipants = new ArrayList<>();
        List<Interaction> allInteractions = new ArrayList<>();
        List<Survey> allSurveys = new ArrayList<>();
        Set<String> seenEmails = new HashSet<>();
        int[] sizes = {90, 55, 38};
        int counter = 0;

        for (int e = 0; e < demoEvents.size(); e++) {
            Event ev = demoEvents.get(e);
            List<Activity> evActs = demoActivities.stream().filter(a -> a.getEventId().equals(ev.getId())).toList();
            Instant evStart = ev.getDate().atZone(ZoneId.of(timezone)).withHour(10).withMinute(0).withSecond(0).withNano(0).toInstant();
            boolean future = evStart.isAfter(now);
            List<Person> chosen = new ArrayList<>(people);
            Collections.shuffle(chosen, rnd);
            chosen = chosen.subList(0, sizes[e]);

            for (Person person : chosen) {
                counter++;
                String id = "dp" + counter;
                boolean attended = !future && rnd.nextDouble() < 0.72;
                Instant arrival = evStart.plus(rnd.nextInt(8 * 60), ChronoUnit.MINUTES);
                boolean consent = rnd.nextDouble() < 0.62;
                List<String> prefs = new ArrayList<>(ev.getProductIds());
                Collections.shuffle(prefs, rnd);
                prefs = new ArrayList<>(prefs.subList(0, Math.min(prefs.size(), 1 + rnd.nextInt(2))));

                allParticipants.add(Participant.builder().id(id).eventId(ev.getId()).firstName(person.firstName())
                        .lastName(person.lastName()).phone(person.phone()).email(person.email()).city(person.city())
                        .ageRange(person.ageRange()).returning(seenEmails.contains(person.email())).preferences(prefs)
                        .consent(consent).source(pick(rnd, SOURCES)).campaign(ev.getCampaign())
                        .qrCode(String.format("CC-D%07d", counter))
                        .registeredAt(evStart.minus(1 + rnd.nextInt(10), ChronoUnit.DAYS))
                        .checkedInAt(attended ? arrival : null)
                        .checkedOutAt(attended && rnd.nextDouble() < 0.85 ? arrival.plus(Duration.ofMinutes(30 + rnd.nextInt(120))) : null)
                        .build());

                if (!attended) continue;
                for (Activity act : evActs) {
                    Interaction base = Interaction.builder().eventId(ev.getId()).participantId(id).activityId(act.getId())
                            .at(arrival.plus(rnd.nextInt(90), ChronoUnit.MINUTES)).build();
                    if (act.getType() == ActivityType.TASTING && rnd.nextDouble() < 0.6) {
                        allInteractions.add(copy(base, InteractionType.TASTING, pick(rnd, ev.getProductIds()),
                                2 + rnd.nextInt(4), rnd.nextDouble() < 0.7, consent));
                    } else if (act.getType() == ActivityType.REDEEM && rnd.nextDouble() < 0.3) {
                        allInteractions.add(copy(base, InteractionType.REDEEM, null, null, null, null));
                        if (rnd.nextDouble() < 0.7) allInteractions.add(copy(base, InteractionType.CONVERSION, null, null, null, null));
                    } else if (act.getType() != ActivityType.TASTING && act.getType() != ActivityType.REDEEM && rnd.nextDouble() < 0.45) {
                        allInteractions.add(copy(base, InteractionType.ACTIVITY, null, null, null, null));
                    }
                }
                if (!future && rnd.nextDouble() < 0.5) {
                    allSurveys.add(Survey.builder().eventId(ev.getId()).participantId(id).organization(score(rnd)).service(score(rnd))
                            .experiences(score(rnd)).products(score(rnd)).overall(score(rnd))
                            .nps(Math.min(10, 5 + rnd.nextInt(6))).createdAt(arrival.plus(90, ChronoUnit.MINUTES)).build());
                }
            }
            chosen.forEach(p -> seenEmails.add(p.email()));
        }

        participants.saveAll(allParticipants);
        int i = 0;
        for (Interaction it : allInteractions) it.setId("di" + (++i));
        interactions.saveAll(allInteractions);
        int s = 0;
        for (Survey sv : allSurveys) sv.setId("ds" + (++s));
        surveys.saveAll(allSurveys);
        log.info("Datos de demostración cargados: {} participantes, {} interacciones, {} encuestas",
                allParticipants.size(), allInteractions.size(), allSurveys.size());
    }

    /** Traduce nombres de demo cargados antes (solo si siguen con el valor original). */
    private void renameLegacyDemoNames() {
        events.findById("e3").filter(e -> "Sampling Sabores del Barrio".equals(e.getName())).ifPresent(e -> {
            e.setName("Muestras Sabores del Barrio");
            events.save(e);
        });
        for (String id : List.of("e2", "e3")) {
            events.findById(id).filter(e -> "Monica Marketing".equals(e.getManager())).ifPresent(e -> {
                e.setManager("Mónica Marketing");
                events.save(e);
            });
        }
        renameActivity("a8", "Mesa de sampling", "Mesa de muestras");
        renameActivity("a2", "Photocall Coca-Cola", "Cabina de fotos Coca-Cola");
    }

    private void renameActivity(String id, String oldName, String newName) {
        activities.findById(id).filter(a -> oldName.equals(a.getName())).ifPresent(a -> {
            a.setName(newName);
            activities.save(a);
        });
    }

    private Event event(String id, String name, String type, int daysFromNow, String location, String organizer,
                        String manager, String description, String objective, String campaign, Long budget,
                        Integer expected, String channel, List<String> productIds, EventStatus status) {
        return Event.builder().id(id).name(name).type(type).date(Instant.now().plus(daysFromNow, ChronoUnit.DAYS))
                .location(location).organizer(organizer).manager(manager).description(description).objective(objective)
                .campaign(campaign).budget(budget).expected(expected).channel(channel).productIds(productIds).status(status).build();
    }

    private Activity act(String id, String eventId, String name, ActivityType type) {
        return Activity.builder().id(id).eventId(eventId).name(name).type(type).build();
    }

    private Interaction copy(Interaction base, InteractionType type, String productId, Integer rating, Boolean wouldBuy, Boolean promos) {
        return Interaction.builder().eventId(base.getEventId()).participantId(base.getParticipantId())
                .activityId(base.getActivityId()).at(base.getAt()).type(type).productId(productId).rating(rating)
                .wouldBuy(wouldBuy).wantsPromos(promos).build();
    }

    private static int score(Random rnd) {
        return Math.min(5, 3 + rnd.nextInt(3));
    }

    private static <T> T pick(Random rnd, List<T> list) {
        return list.get(rnd.nextInt(list.size()));
    }

    private static String plain(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
