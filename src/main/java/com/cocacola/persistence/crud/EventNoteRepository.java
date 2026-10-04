package com.cocacola.persistence.crud;

import com.cocacola.persistence.entity.EventNoteEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventNoteRepository extends JpaRepository<EventNoteEntity, String> {
    List<EventNoteEntity> findByEventIdOrderByCreatedAtDesc(String eventId);
    void deleteByEventId(String eventId);
}
