package com.cocacola.persistence.mapper;

import com.cocacola.domain.model.User;
import com.cocacola.persistence.entity.UserEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    User toDomain(UserEntity entity);

    UserEntity toEntity(User domain);
}
