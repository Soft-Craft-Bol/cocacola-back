package com.cocacola.persistence.crud;

import com.cocacola.domain.model.Survey;
import com.cocacola.domain.repository.SurveyRepository;
import com.cocacola.persistence.mapper.SurveyMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@RequiredArgsConstructor
public class SurveyRepositoryImpl implements SurveyRepository {

    private final SurveyCrudRepository crud;
    private final SurveyMapper mapper;

    @Override
    public List<Survey> findAll() {
        return crud.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Survey> findByEventId(String eventId) {
        return crud.findByEventId(eventId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public boolean existsByParticipantId(String participantId) {
        return crud.existsByParticipantId(participantId);
    }

    @Override
    public Survey save(Survey survey) {
        return mapper.toDomain(crud.save(mapper.toEntity(survey)));
    }

    @Override
    public List<Survey> saveAll(List<Survey> items) {
        return crud.saveAll(items.stream().map(mapper::toEntity).toList()).stream().map(mapper::toDomain).toList();
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
