package com.cocacola.persistence.crud;

import com.cocacola.persistence.entity.NotificationEntity;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationCrudRepository extends JpaRepository<NotificationEntity, String> {

    List<NotificationEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    List<NotificationEntity> findByReadFalseOrderByCreatedAtDesc(Pageable pageable);

    long countByReadFalse();

    @Query("select n from NotificationEntity n where n.eventId in :ids and (:unread = false or n.read = false) order by n.createdAt desc")
    List<NotificationEntity> findForEvents(@org.springframework.data.repository.query.Param("ids") java.util.Set<String> ids,
            @org.springframework.data.repository.query.Param("unread") boolean unread, Pageable pageable);
    long countByReadFalseAndEventIdIn(java.util.Set<String> ids);
    @Modifying
    @Query("update NotificationEntity n set n.read = true where n.eventId in :ids and n.read = false")
    void markReadForEvents(@org.springframework.data.repository.query.Param("ids") java.util.Set<String> ids);

    @Modifying
    @Query("update NotificationEntity n set n.read = true where n.read = false")
    int markAllRead();
}
