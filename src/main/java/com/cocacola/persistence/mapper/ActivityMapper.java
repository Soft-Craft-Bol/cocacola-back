package com.cocacola.persistence.mapper;

import com.cocacola.domain.model.Activity;
import com.cocacola.persistence.entity.ActivityEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ActivityMapper {

    Activity toDomain(ActivityEntity entity);

    ActivityEntity toEntity(Activity domain);
}
