package com.cocacola.persistence.crud;

import com.cocacola.persistence.entity.ActivityEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActivityCrudRepository extends JpaRepository<ActivityEntity, String> {
    List<ActivityEntity> findByEventId(String eventId);
    void deleteByEventId(String eventId);
}
