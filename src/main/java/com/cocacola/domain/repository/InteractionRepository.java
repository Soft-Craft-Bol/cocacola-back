package com.cocacola.domain.repository;

import com.cocacola.domain.model.Interaction;
import java.util.List;
import java.util.Optional;

public interface InteractionRepository {

    List<Interaction> findAll();
    List<Interaction> findByEventId(String eventId);
    List<Interaction> findByParticipantId(String participantId);
    Interaction save(Interaction interaction);

    List<Interaction> saveAll(List<Interaction> items);
    void deleteById(String id);
    void deleteByEventId(String eventId);
    void deleteByParticipantId(String participantId);
}
