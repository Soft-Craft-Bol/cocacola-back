package com.cocacola.domain.service;

import com.cocacola.domain.helpers.ConflictException;
import com.cocacola.domain.helpers.IdGenerator;
import com.cocacola.domain.helpers.NotFoundException;
import com.cocacola.domain.model.Survey;
import com.cocacola.domain.repository.ParticipantRepository;
import com.cocacola.domain.repository.SurveyRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SurveyService {

    private final SurveyRepository surveys;
    private final ParticipantRepository participants;
    private final NotificationService notifications;
    private final EventAccessService access;
    private final EventOperationsService operations;

    public List<Survey> list(String eventId) {
        if (eventId != null && !eventId.isBlank()) access.require(eventId);
        var scope = access.assignedEventIds();
        return (eventId == null || eventId.isBlank() ? surveys.findAll() : surveys.findByEventId(eventId))
                .stream().filter(s -> scope == null || scope.contains(s.getEventId())).toList();
    }

    public Survey create(Survey data) {
        access.require(data.getEventId());
        var participant = participants.findById(data.getParticipantId()).orElseThrow(() -> new NotFoundException("Participante"));
        access.require(participant.getEventId());
        if (!participant.getEventId().equals(data.getEventId())) throw new IllegalArgumentException("El participante pertenece a otro evento");
        if (surveys.existsByParticipantId(data.getParticipantId())) {
            throw new ConflictException("Este participante ya respondió la encuesta");
        }
        data.setId(IdGenerator.newId());
        data.setCreatedAt(Instant.now());
        Survey saved = surveys.save(data);
        if (saved.getNps() <= operations.settings(saved.getEventId()).lowNpsThreshold()) {
            participants.findById(saved.getParticipantId()).ifPresent(p -> notifications.notify("NPS_BAJO", "warning",
                    "Encuesta con NPS bajo", p.getFirstName() + " " + p.getLastName() + " calificó con " + saved.getNps()
                            + " la probabilidad de recomendar la experiencia.", saved.getEventId()));
        }
        return saved;
    }
}
