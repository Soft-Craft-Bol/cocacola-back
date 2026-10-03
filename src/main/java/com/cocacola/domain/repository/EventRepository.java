package com.cocacola.domain.repository;

import com.cocacola.domain.model.Event;
import java.util.List;
import java.util.Optional;

public interface EventRepository {

    List<Event> findAll();
    Optional<Event> findById(String id);
    Event save(Event event);
    void deleteById(String id);
}
