package com.cocacola.application.rest.controller;

import com.cocacola.application.request.ActivityRequest;
import com.cocacola.application.response.ActivityResponse;
import com.cocacola.application.response.OkResponse;
import com.cocacola.domain.model.Activity;
import com.cocacola.domain.service.ActivityService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activities;

    @GetMapping
    public List<ActivityResponse> list(@RequestParam(required = false) String eventId) {
        return activities.list(eventId).stream().map(ActivityResponse::from).toList();
    }

    @PostMapping
    public ActivityResponse create(@Valid @RequestBody ActivityRequest r) {
        return ActivityResponse.from(activities.create(
                Activity.builder().eventId(r.eventId()).name(r.name()).type(r.type()).experienceId(r.experienceId()).build()));
    }

    @DeleteMapping("/{id}")
    public OkResponse delete(@PathVariable String id) {
        activities.delete(id);
        return OkResponse.done();
    }
}
