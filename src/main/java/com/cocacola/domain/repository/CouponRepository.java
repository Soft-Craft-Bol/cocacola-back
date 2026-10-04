package com.cocacola.domain.repository;

import com.cocacola.domain.model.Coupon;
import java.util.List;
import java.util.Optional;

public interface CouponRepository {

    List<Coupon> findAll();
    List<Coupon> findByEventId(String eventId);
    List<Coupon> findByParticipantId(String participantId);
    Optional<Coupon> findById(String id);
    Optional<Coupon> findByCode(String code);
    boolean existsByCode(String code);
    Coupon save(Coupon coupon);
    void deleteById(String id);
    void deleteByEventId(String eventId);
    void deleteByParticipantId(String participantId);
}
