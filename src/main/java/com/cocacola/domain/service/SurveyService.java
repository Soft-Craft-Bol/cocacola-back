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

    public List<Survey> list(String eventId) {
        return eventId == null || eventId.isBlank() ? surveys.findAll() : surveys.findByEventId(eventId);
    }

    public Survey create(Survey data) {
        participants.findById(data.getParticipantId()).orElseThrow(() -> new NotFoundException("Participante"));
        if (surveys.existsByParticipantId(data.getParticipantId())) {
            throw new ConflictException("Este participante ya respondió la encuesta");
        }
        data.setId(IdGenerator.newId());
        data.setCreatedAt(Instant.now());
        Survey saved = surveys.save(data);
        if (saved.getNps() <= 6) {
            participants.findById(saved.getParticipantId()).ifPresent(p -> notifications.notify("NPS_BAJO", "warning",
                    "Encuesta con NPS bajo", p.getFirstName() + " " + p.getLastName() + " calificó con " + saved.getNps()
                            + " la probabilidad de recomendar la experiencia.", saved.getEventId()));
        }
        return saved;
    }
}
