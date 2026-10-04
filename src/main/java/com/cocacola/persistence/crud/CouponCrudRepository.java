package com.cocacola.persistence.crud;

import com.cocacola.persistence.entity.CouponEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CouponCrudRepository extends JpaRepository<CouponEntity, String> {
    List<CouponEntity> findByEventId(String eventId);
    List<CouponEntity> findByParticipantId(String participantId);
    Optional<CouponEntity> findByCode(String code);
    boolean existsByCode(String code);
    void deleteByEventId(String eventId);
    void deleteByParticipantId(String participantId);
}
