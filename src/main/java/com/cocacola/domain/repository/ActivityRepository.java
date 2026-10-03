package com.cocacola.domain.repository;

import com.cocacola.domain.model.Activity;
import java.util.List;
import java.util.Optional;

public interface ActivityRepository {

    List<Activity> findAll();
    List<Activity> findByEventId(String eventId);
    Optional<Activity> findById(String id);
    Activity save(Activity activity);
    void deleteById(String id);
    void deleteByEventId(String eventId);
}
