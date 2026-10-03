package com.cocacola.persistence.mapper;

import com.cocacola.domain.model.Interaction;
import com.cocacola.persistence.entity.InteractionEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface InteractionMapper {

    Interaction toDomain(InteractionEntity entity);

    InteractionEntity toEntity(Interaction domain);
}
