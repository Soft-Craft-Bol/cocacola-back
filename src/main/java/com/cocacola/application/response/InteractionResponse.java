package com.cocacola.application.response;

import com.cocacola.commons.enums.InteractionType;
import com.cocacola.domain.model.Interaction;
import java.time.Instant;

public record InteractionResponse(
        String id, String eventId, String participantId, String activityId, InteractionType type, String productId,
        Integer rating, Boolean wouldBuy, Boolean wantsPromos, Instant at) {

    public static InteractionResponse from(Interaction i) {
        return new InteractionResponse(i.getId(), i.getEventId(), i.getParticipantId(), i.getActivityId(), i.getType(),
                i.getProductId(), i.getRating(), i.getWouldBuy(), i.getWantsPromos(), i.getAt());
    }
}
