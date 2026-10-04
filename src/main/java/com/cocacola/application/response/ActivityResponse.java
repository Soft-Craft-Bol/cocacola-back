package com.cocacola.application.response;

import com.cocacola.commons.enums.ActivityType;
import com.cocacola.domain.model.Activity;

public record ActivityResponse(String id, String eventId, String name, ActivityType type, String experienceId) {

    public static ActivityResponse from(Activity a) {
        return new ActivityResponse(a.getId(), a.getEventId(), a.getName(), a.getType(), a.getExperienceId());
    }
}
