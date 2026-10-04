package com.cocacola.domain.service;

import com.cocacola.commons.enums.EventStatus;
import com.cocacola.domain.helpers.NotFoundException;
import com.cocacola.domain.model.Event;
import com.cocacola.domain.model.Participant;
import com.cocacola.domain.repository.EventRepository;
import com.cocacola.domain.repository.ParticipantRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Consultas públicas (sin sesión): eventos abiertos a inscripción y recuperación de la entrada del asistente. */
@Service
@RequiredArgsConstructor
public class PublicService {

    public record PublicEvent(String id, String name, String type, Instant date, String location, String description,
                              String campaign, String imageUrl, String status) {
    }

    public record Ticket(String participantName, String eventId, String eventName, Instant eventDate, String location,
                         String eventStatus, String qrCode, boolean attended, String registrationType) {
    }

    private final EventRepository events;
    private final ParticipantRepository participants;

    /** Eventos planificados o en curso, del más próximo al más lejano. */
    public List<PublicEvent> upcomingEvents() {
        return events.findAll().stream()
                .filter(e -> e.getStatus() != EventStatus.FINISHED)
                .sorted(Comparator.comparing(Event::getDate))
                .map(e -> new PublicEvent(e.getId(), e.getName(), e.getType(), e.getDate(), e.getLocation(), e.getDescription(),
                        e.getCampaign(), e.getImageUrl(), e.getStatus() == null ? null : e.getStatus().getValue()))
                .toList();
    }

    /** Entradas vigentes de una persona, por correo o celular (de eventos que aún no finalizaron). */
    public List<Ticket> lookup(String contact) {
        String value = contact == null ? "" : contact.trim();
        String email = null;
        String digits = null;
        if (value.contains("@")) {
            email = value.toLowerCase();
        } else {
            String only = value.replaceAll("\\D", "");
            if (only.length() < 7) throw new IllegalArgumentException("Escribe un correo o un celular válido");
            digits = only.length() > 10 ? only.substring(only.length() - 10) : only;
        }
        List<Participant> found = participants.findByContact(email, digits);
        Map<String, Event> byId = events.findAll().stream().collect(Collectors.toMap(Event::getId, Function.identity(), (a, b) -> a));
        return found.stream()
                .filter(p -> byId.containsKey(p.getEventId()) && byId.get(p.getEventId()).getStatus() != EventStatus.FINISHED)
                .map(p -> ticket(p, byId.get(p.getEventId())))
                .sorted(Comparator.comparing(Ticket::eventDate))
                .toList();
    }

    public Ticket byCode(String code) {
        Participant p = participants.findByQrCode(code.trim().toUpperCase()).orElseThrow(() -> new NotFoundException("Entrada"));
        Event e = events.findById(p.getEventId()).orElseThrow(() -> new NotFoundException("Evento"));
        return ticket(p, e);
    }

    private static Ticket ticket(Participant p, Event e) {
        return new Ticket(p.getFirstName() + " " + p.getLastName(), e.getId(), e.getName(), e.getDate(), e.getLocation(),
                e.getStatus() == null ? null : e.getStatus().getValue(), p.getQrCode(), p.getCheckedInAt() != null,
                // antes del evento es una pre-inscripción; durante el evento, una inscripción
                e.getStatus() == EventStatus.ACTIVE ? "Inscripción" : "Pre-inscripción");
    }
}
