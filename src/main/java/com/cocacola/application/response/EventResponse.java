package com.cocacola.application.response;

import com.cocacola.commons.enums.EventStatus;
import com.cocacola.domain.model.Event;
import java.time.Instant;
import java.util.List;

public record EventResponse(
        String id, String name, String type, Instant date, String location, String organizer, String manager,
        String description, String objective, String campaign, Long budget, Integer expected, String channel,
        List<String> productIds, EventStatus status, String imageUrl, List<String> experienceIds) {

    public static EventResponse from(Event e) {
        return new EventResponse(e.getId(), e.getName(), e.getType(), e.getDate(), e.getLocation(), e.getOrganizer(),
                e.getManager(), e.getDescription(), e.getObjective(), e.getCampaign(), e.getBudget(), e.getExpected(),
                e.getChannel(), e.getProductIds(), e.getStatus(), e.getImageUrl(), e.getExperienceIds() == null ? List.of() : e.getExperienceIds());
    }
}
