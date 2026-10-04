package com.cocacola.persistence.crud;

import com.cocacola.persistence.entity.EventOperationsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventOperationsRepository extends JpaRepository<EventOperationsEntity, String> {
    java.util.List<EventOperationsEntity> findByOrganizerUserIdOrManagerUserId(String organizerUserId, String managerUserId);
    boolean existsByOrganizerUserIdOrManagerUserId(String organizerUserId, String managerUserId);
}
