package com.cocacola.domain.model.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record EvolutionPoint(
        String name,
        Instant date,
        @JsonProperty("Asistentes") int attendees,
        @JsonProperty("Recurrentes") int returning) {
}
