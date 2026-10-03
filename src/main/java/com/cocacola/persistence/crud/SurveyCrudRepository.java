package com.cocacola.persistence.crud;

import com.cocacola.persistence.entity.SurveyEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyCrudRepository extends JpaRepository<SurveyEntity, String> {
    List<SurveyEntity> findByEventId(String eventId);
    boolean existsByParticipantId(String participantId);
    void deleteByEventId(String eventId);
    void deleteByParticipantId(String participantId);
}
