package com.cocacola.domain.service;

import com.cocacola.domain.helpers.ConflictException;
import com.cocacola.domain.helpers.NotFoundException;
import com.cocacola.domain.model.Event;
import com.cocacola.domain.model.Participant;
import com.cocacola.domain.model.Survey;
import com.cocacola.domain.repository.EventRepository;
import com.cocacola.domain.repository.ParticipantRepository;
import com.cocacola.domain.repository.SurveyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Encuesta posterior al evento que responde el propio asistente con el código de su QR. */
@Service
@RequiredArgsConstructor
public class PublicSurveyService {

    public record SurveyInfo(String participantName, String eventName, boolean attended, boolean answered) {
    }

    private final ParticipantRepository participants;
    private final EventRepository events;
    private final SurveyRepository surveys;
    private final SurveyService surveyService;

    public SurveyInfo info(String code) {
        Participant p = find(code);
        Event e = events.findById(p.getEventId()).orElseThrow(() -> new NotFoundException("Evento"));
        return new SurveyInfo(p.getFirstName(), e.getName(), p.getCheckedInAt() != null, surveys.existsByParticipantId(p.getId()));
    }

    public Survey submit(String code, Survey answers) {
        Participant p = find(code);
        if (p.getCheckedInAt() == null) throw new ConflictException("Solo quienes asistieron al evento pueden responder la encuesta");
        answers.setEventId(p.getEventId());
        answers.setParticipantId(p.getId());
        return surveyService.create(answers);
    }

    private Participant find(String code) {
        return participants.findByQrCode(code.trim().toUpperCase()).orElseThrow(() -> new NotFoundException("Código"));
    }
}
