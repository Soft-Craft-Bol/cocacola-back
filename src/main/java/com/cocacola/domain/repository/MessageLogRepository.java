package com.cocacola.domain.repository;

import com.cocacola.domain.model.MessageLog;
import java.util.List;

public interface MessageLogRepository {

    MessageLog save(MessageLog log);

    boolean existsSent(String participantId, String type, String channel);

    List<MessageLog> findRecentByEventId(String eventId, int limit);
}
