package com.cocacola.persistence.mapper;

import com.cocacola.domain.model.MessageLog;
import com.cocacola.persistence.entity.MessageLogEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface MessageLogMapper {

    MessageLog toDomain(MessageLogEntity entity);

    MessageLogEntity toEntity(MessageLog domain);
}
