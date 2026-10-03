package com.cocacola.domain.service;

import com.cocacola.domain.helpers.IdGenerator;
import com.cocacola.domain.helpers.NotFoundException;
import com.cocacola.domain.model.Activity;
import com.cocacola.domain.repository.ActivityRepository;
import com.cocacola.domain.repository.EventRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityRepository activities;
    private final EventRepository events;

    public List<Activity> list(String eventId) {
        return eventId == null || eventId.isBlank() ? activities.findAll() : activities.findByEventId(eventId);
    }

    public Activity create(Activity data) {
        events.findById(data.getEventId()).orElseThrow(() -> new NotFoundException("Evento"));
        data.setId(IdGenerator.newId());
        return activities.save(data);
    }

    public void delete(String id) {
        activities.deleteById(id);
    }
}
