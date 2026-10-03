package com.cocacola.persistence.mapper;

import com.cocacola.domain.model.Notification;
import com.cocacola.persistence.entity.NotificationEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    Notification toDomain(NotificationEntity entity);

    NotificationEntity toEntity(Notification domain);
}
