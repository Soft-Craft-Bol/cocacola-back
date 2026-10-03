package com.cocacola.persistence.mapper;

import com.cocacola.domain.model.Survey;
import com.cocacola.persistence.entity.SurveyEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SurveyMapper {

    Survey toDomain(SurveyEntity entity);

    SurveyEntity toEntity(Survey domain);
}
