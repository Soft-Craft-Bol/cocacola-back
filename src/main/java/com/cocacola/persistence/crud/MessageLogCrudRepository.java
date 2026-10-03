package com.cocacola.persistence.crud;

import com.cocacola.persistence.entity.MessageLogEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageLogCrudRepository extends JpaRepository<MessageLogEntity, String> {

    boolean existsByParticipantIdAndTypeAndChannelAndStatus(String participantId, String type, String channel, String status);

    List<MessageLogEntity> findByEventIdOrderBySentAtDesc(String eventId, Pageable pageable);
}
