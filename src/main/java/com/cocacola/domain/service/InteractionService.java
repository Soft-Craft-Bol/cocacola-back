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

    public List<Interaction> list(String eventId, String participantId) {
        List<Interaction> all;
        if (participantId != null && !participantId.isBlank()) all = interactions.findByParticipantId(participantId);
        else if (eventId != null && !eventId.isBlank()) all = interactions.findByEventId(eventId);
        else all = interactions.findAll();
        return all.stream()
                .filter(i -> eventId == null || eventId.isBlank() || eventId.equals(i.getEventId()))
                .sorted(Comparator.comparing(Interaction::getAt).reversed())
                .toList();
    }

    public Interaction create(Interaction data) {
        Participant p = participants.findById(data.getParticipantId()).orElseThrow(() -> new NotFoundException("Participante"));
        if (p.getCheckedInAt() == null) {
            throw new ConflictException("El participante debe registrar su ingreso antes de interactuar");
        }
        data.setId(IdGenerator.newId());
        data.setAt(Instant.now());
        Interaction saved = interactions.save(data);
        if (saved.getType() == com.cocacola.commons.enums.InteractionType.CONVERSION) {
            long conversions = interactions.findByEventId(saved.getEventId()).stream()
                    .filter(i -> i.getType() == com.cocacola.commons.enums.InteractionType.CONVERSION).count();
            if (conversions % 10 == 0) {
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
