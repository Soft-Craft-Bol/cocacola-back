package com.cocacola.persistence.crud;

import com.cocacola.domain.model.Interaction;
import com.cocacola.domain.repository.InteractionRepository;
import com.cocacola.persistence.mapper.InteractionMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@RequiredArgsConstructor
public class InteractionRepositoryImpl implements InteractionRepository {

    private final InteractionCrudRepository crud;
    private final InteractionMapper mapper;

    @Override
    public List<Interaction> findAll() {
        return crud.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Interaction> findByEventId(String eventId) {
        return crud.findByEventId(eventId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Interaction> findByParticipantId(String participantId) {
        return crud.findByParticipantId(participantId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Interaction save(Interaction interaction) {
        return mapper.toDomain(crud.save(mapper.toEntity(interaction)));
    }

    @Override
    public List<Interaction> saveAll(List<Interaction> items) {
        return crud.saveAll(items.stream().map(mapper::toEntity).toList()).stream().map(mapper::toDomain).toList();
    }

    @Override
    public void deleteById(String id) {
        crud.deleteById(id);
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
