package com.cocacola.persistence.mapper;

import com.cocacola.domain.model.Coupon;
import com.cocacola.persistence.entity.CouponEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CouponMapper {

    Coupon toDomain(CouponEntity entity);

    CouponEntity toEntity(Coupon domain);
}
