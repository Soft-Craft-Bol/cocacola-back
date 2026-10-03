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

    @Modifying
    @Query("update NotificationEntity n set n.read = true where n.read = false")
    int markAllRead();
}
