package com.cocacola.persistence.crud;

import com.cocacola.domain.model.Event;
import com.cocacola.domain.repository.EventRepository;
import com.cocacola.persistence.mapper.EventMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@RequiredArgsConstructor
public class EventRepositoryImpl implements EventRepository {

    private final EventCrudRepository crud;
    private final EventMapper mapper;

    @Override
    @Transactional(readOnly = true)
    public List<Event> findAll() {
        return crud.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Event> findById(String id) {
        return crud.findById(id).map(mapper::toDomain);
    }

    @Override
    public Event save(Event event) {
        return mapper.toDomain(crud.save(mapper.toEntity(event)));
    }

    @Override
    public void deleteById(String id) {
        crud.deleteById(id);
    }
}
