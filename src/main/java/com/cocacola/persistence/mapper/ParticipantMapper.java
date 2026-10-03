package com.cocacola.persistence.mapper;

import com.cocacola.domain.model.Participant;
import com.cocacola.persistence.entity.ParticipantEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ParticipantMapper {

    Participant toDomain(ParticipantEntity entity);

    ParticipantEntity toEntity(Participant domain);
}
