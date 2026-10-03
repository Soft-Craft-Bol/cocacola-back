package com.cocacola.persistence.crud;

import com.cocacola.persistence.entity.InteractionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InteractionCrudRepository extends JpaRepository<InteractionEntity, String> {
    List<InteractionEntity> findByEventId(String eventId);
    List<InteractionEntity> findByParticipantId(String participantId);
    void deleteByEventId(String eventId);
    void deleteByParticipantId(String participantId);
}
