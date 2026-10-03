package com.cocacola.persistence.crud;

import com.cocacola.domain.model.Activity;
import com.cocacola.domain.repository.ActivityRepository;
import com.cocacola.persistence.mapper.ActivityMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@RequiredArgsConstructor
public class ActivityRepositoryImpl implements ActivityRepository {

    private final ActivityCrudRepository crud;
    private final ActivityMapper mapper;

    @Override
    public List<Activity> findAll() {
        return crud.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public List<Activity> findByEventId(String eventId) {
        return crud.findByEventId(eventId).stream().map(mapper::toDomain).toList();
    }

    @Override
    public Optional<Activity> findById(String id) {
        return crud.findById(id).map(mapper::toDomain);
    }

    @Override
    public Activity save(Activity activity) {
        return mapper.toDomain(crud.save(mapper.toEntity(activity)));
    }

    @Override
    public void deleteById(String id) {
        crud.deleteById(id);
    }

    @Override
    public void deleteByEventId(String eventId) {
        crud.deleteByEventId(eventId);
    }
}
