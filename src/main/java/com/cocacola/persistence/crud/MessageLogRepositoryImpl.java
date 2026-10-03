package com.cocacola.persistence.crud;

import com.cocacola.domain.model.MessageLog;
import com.cocacola.domain.repository.MessageLogRepository;
import com.cocacola.persistence.mapper.MessageLogMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@RequiredArgsConstructor
public class MessageLogRepositoryImpl implements MessageLogRepository {

    private final MessageLogCrudRepository crud;
    private final MessageLogMapper mapper;

    @Override
    public MessageLog save(MessageLog log) {
        return mapper.toDomain(crud.save(mapper.toEntity(log)));
    }

    @Override
    public boolean existsSent(String participantId, String type, String channel) {
        return crud.existsByParticipantIdAndTypeAndChannelAndStatus(participantId, type, channel, "SENT");
    }

    @Override
    public List<MessageLog> findRecentByEventId(String eventId, int limit) {
        return crud.findByEventIdOrderBySentAtDesc(eventId, PageRequest.of(0, Math.max(1, Math.min(limit, 200))))
                .stream().map(mapper::toDomain).toList();
    }
}
