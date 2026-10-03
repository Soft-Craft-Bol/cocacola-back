package com.cocacola.persistence.crud;

import com.cocacola.persistence.entity.EventEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

public interface EventCrudRepository extends JpaRepository<EventEntity, String> {
    @Override
    @EntityGraph(attributePaths = "productIds")
    List<EventEntity> findAll();

    @Override
    @EntityGraph(attributePaths = "productIds")
    Optional<EventEntity> findById(String id);
}
