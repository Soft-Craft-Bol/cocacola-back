package com.cocacola.persistence.crud;

import com.cocacola.domain.model.Coupon;
import com.cocacola.domain.repository.CouponRepository;
import com.cocacola.persistence.mapper.CouponMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@RequiredArgsConstructor
public class CouponRepositoryImpl implements CouponRepository {

    private final CouponCrudRepository crud;
    private final CouponMapper mapper;

    @Override
    public List<Coupon> findAll() {
        return crud.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Coupon> findByEventId(String eventId) {
        return crud.findByEventId(eventId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Coupon> findByParticipantId(String participantId) {
        return crud.findByParticipantId(participantId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Coupon> findById(String id) {
        return crud.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Coupon> findByCode(String code) {
        return crud.findByCode(code).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(String code) {
        return crud.existsByCode(code);
    }

    @Override
    public Coupon save(Coupon coupon) {
        return mapper.toDomain(crud.save(mapper.toEntity(coupon)));
    }

    @Override
    public void deleteById(String id) {
        crud.deleteById(id);
    }

    @Override
    public void deleteByEventId(String eventId) {
        crud.deleteByEventId(eventId);
    }

    @Override
    public void deleteByParticipantId(String participantId) {
        crud.deleteByParticipantId(participantId);
    }
}
