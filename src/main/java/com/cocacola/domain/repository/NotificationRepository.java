package com.cocacola.domain.repository;

import com.cocacola.domain.model.Notification;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(String id);

    List<Notification> findRecent(boolean unreadOnly, int limit);

    long countUnread();

    void markAllRead();
}
