package com.cocacola.domain.service;

import com.cocacola.domain.helpers.ConflictException;
import com.cocacola.domain.helpers.IdGenerator;
import com.cocacola.domain.helpers.NotFoundException;
import com.cocacola.domain.model.Interaction;
import com.cocacola.domain.model.Participant;
import com.cocacola.domain.repository.InteractionRepository;
import com.cocacola.domain.repository.ParticipantRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InteractionService {

    private final InteractionRepository interactions;
    private final ParticipantRepository participants;
    private final NotificationService notifications;
    private final EventAccessService access;
    private final EventOperationsService operations;
    private final com.cocacola.domain.repository.ActivityRepository activities;
    private final com.cocacola.domain.repository.EventRepository events;
    private final com.cocacola.domain.repository.ProductRepository products;

    public List<Interaction> list(String eventId, String participantId) {
        if (eventId != null && !eventId.isBlank()) access.require(eventId);
        if (participantId != null && !participantId.isBlank()) {
            access.require(participants.findById(participantId).orElseThrow(() -> new NotFoundException("Participante")).getEventId());
        }
        List<Interaction> all;
        if (participantId != null && !participantId.isBlank()) all = interactions.findByParticipantId(participantId);
        else if (eventId != null && !eventId.isBlank()) all = interactions.findByEventId(eventId);
        else all = interactions.findAll();
        var scope = access.assignedEventIds();
        return all.stream()
                .filter(i -> scope == null || scope.contains(i.getEventId()))
                .filter(i -> eventId == null || eventId.isBlank() || eventId.equals(i.getEventId()))
                .sorted(Comparator.comparing(Interaction::getAt).reversed())
                .toList();
    }

    public Interaction create(Interaction data) {
        access.require(data.getEventId());
        Participant p = participants.findById(data.getParticipantId()).orElseThrow(() -> new NotFoundException("Participante"));
        access.require(p.getEventId());
        if (!p.getEventId().equals(data.getEventId())) throw new IllegalArgumentException("El participante pertenece a otro evento");
        if (data.getActivityId() != null && !data.getActivityId().isBlank()) {
            var activity = activities.findById(data.getActivityId()).orElseThrow(() -> new NotFoundException("Actividad"));
            if (!activity.getEventId().equals(data.getEventId())) throw new IllegalArgumentException("La actividad pertenece a otro evento");
        }
        if (p.getCheckedInAt() == null) {
            throw new ConflictException("El participante debe registrar su ingreso antes de interactuar");
        }
        if (data.getType() == com.cocacola.commons.enums.InteractionType.TASTING || data.getProductId() != null) {
            var event = events.findById(data.getEventId()).orElseThrow(() -> new NotFoundException("Evento"));
            if (data.getProductId() == null || data.getProductId().isBlank() || products.findById(data.getProductId()).isEmpty()
                    || event.getProductIds() == null || !event.getProductIds().contains(data.getProductId()))
                throw new IllegalArgumentException("Selecciona un producto destacado de este evento");
        }
        boolean alreadyConverted = data.getType() == com.cocacola.commons.enums.InteractionType.CONVERSION
                && interactions.findByParticipantId(p.getId()).stream()
                .anyMatch(i -> i.getType() == com.cocacola.commons.enums.InteractionType.CONVERSION && data.getEventId().equals(i.getEventId()));
        data.setId(IdGenerator.newId());
        data.setAt(Instant.now());
        Interaction saved = interactions.save(data);
        if (saved.getType() == com.cocacola.commons.enums.InteractionType.CONVERSION && !alreadyConverted) {
            long conversions = interactions.findByEventId(saved.getEventId()).stream()
                    .filter(i -> i.getType() == com.cocacola.commons.enums.InteractionType.CONVERSION)
                    .map(i -> i.getParticipantId()).distinct().count();
            int goal = operations.settings(saved.getEventId()).conversionGoal();
            if (goal > 0 && conversions == goal) {
                notifications.notify("CONVERSIONES", "success", conversions + " conversiones alcanzadas",
                        "El evento llegó a " + conversions + " conversiones registradas.", saved.getEventId());
            }
        }
        return saved;
    }

    public void delete(String id) {
        interactions.deleteById(id);
    }
}
