package com.cocacola.domain.repository;

import com.cocacola.domain.model.Survey;
import java.util.List;
import java.util.Optional;

public interface SurveyRepository {

    List<Survey> findAll();
    List<Survey> findByEventId(String eventId);
    boolean existsByParticipantId(String participantId);
    Survey save(Survey survey);

    List<Survey> saveAll(List<Survey> items);
    void deleteByEventId(String eventId);
    void deleteByParticipantId(String participantId);
}
