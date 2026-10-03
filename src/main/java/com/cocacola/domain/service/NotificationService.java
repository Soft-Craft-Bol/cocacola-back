package com.cocacola.domain.service;

import com.cocacola.domain.helpers.IdGenerator;
import com.cocacola.domain.helpers.NotFoundException;
import com.cocacola.domain.model.Notification;
import com.cocacola.domain.repository.NotificationRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notifications;

    /** Crea un aviso. Nunca debe romper la operación que lo origina. */
    public void notify(String type, String severity, String title, String message, String eventId) {
        try {
            notifications.save(Notification.builder().id(IdGenerator.newId()).type(type).severity(severity)
                    .title(title).message(message).eventId(eventId).createdAt(Instant.now()).read(false).build());
        } catch (RuntimeException ex) {
            log.warn("No se pudo crear la notificación '{}': {}", title, ex.getMessage());
        }
    }

    public List<Notification> list(boolean unreadOnly, int limit) {
        return notifications.findRecent(unreadOnly, limit);
    }

    public long unread() {
        return notifications.countUnread();
    }

    public void markRead(String id) {
        Notification n = notifications.findById(id).orElseThrow(() -> new NotFoundException("Notificación"));
        n.setRead(true);
        notifications.save(n);
    }

    public void markAllRead() {
        notifications.markAllRead();
    }
}
