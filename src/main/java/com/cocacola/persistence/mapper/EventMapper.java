package com.cocacola.persistence.mapper;

import com.cocacola.domain.model.Event;
import com.cocacola.persistence.entity.EventEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EventMapper {

    Event toDomain(EventEntity entity);

    EventEntity toEntity(Event domain);
}
