package com.cocacola.application.request;

import com.cocacola.commons.enums.InteractionType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InteractionRequest(
        @NotBlank String eventId,
        @NotBlank String participantId,
        String activityId,
        @NotNull InteractionType type,
        String productId,
        @Min(1) @Max(5) Integer rating,
        Boolean wouldBuy,
        Boolean wantsPromos) {
}
