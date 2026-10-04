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
    private final EventAccessService access;
    private final com.cocacola.persistence.crud.ExperienceRepository experiences;
    private final com.cocacola.domain.repository.InteractionRepository interactions;

    public List<Activity> list(String eventId) {
        if (eventId != null && !eventId.isBlank()) access.require(eventId);
        var scope = access.assignedEventIds();
        return (eventId == null || eventId.isBlank() ? activities.findAll() : activities.findByEventId(eventId))
                .stream().filter(a -> scope == null || scope.contains(a.getEventId())).toList();
    }

    public Activity create(Activity data) {
        access.require(data.getEventId());
        var event = events.findById(data.getEventId()).orElseThrow(() -> new NotFoundException("Evento"));
        if (data.getExperienceId() != null && data.getExperienceId().isBlank()) data.setExperienceId(null);
        if (data.getExperienceId() != null && (event.getExperienceIds() == null || !event.getExperienceIds().contains(data.getExperienceId())
                || !experiences.existsById(data.getExperienceId())))
            throw new IllegalArgumentException("Selecciona una experiencia destacada de este evento");
        data.setId(IdGenerator.newId());
        return activities.save(data);
    }

    public void delete(String id) {
        var activity = activities.findById(id).orElseThrow(() -> new NotFoundException("Actividad"));
        access.require(activity.getEventId());
        if (interactions.findByEventId(activity.getEventId()).stream().anyMatch(i -> id.equals(i.getActivityId())))
            throw new com.cocacola.domain.helpers.ConflictException("Esta actividad tiene interacciones registradas y debe conservarse en el historial");
        activities.deleteById(id);
    }
}
