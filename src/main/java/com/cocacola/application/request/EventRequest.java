package com.cocacola.application.request;

import com.cocacola.commons.enums.EventStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public record EventRequest(
        @NotBlank String name,
        @NotBlank String type,
        @NotNull Instant date,
        @NotBlank String location,
        String organizer,
        String manager,
        String description,
        String objective,
        String campaign,
        Long budget,
        Integer expected,
        String channel,
        List<String> productIds,
        EventStatus status) {
}
