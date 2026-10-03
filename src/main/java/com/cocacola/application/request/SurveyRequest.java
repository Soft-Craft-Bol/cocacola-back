package com.cocacola.application.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SurveyRequest(
        @NotBlank String eventId,
        @NotBlank String participantId,
        @Min(1) @Max(5) int organization,
        @Min(1) @Max(5) int service,
        @Min(1) @Max(5) int experiences,
        @Min(1) @Max(5) int products,
        @Min(1) @Max(5) int overall,
        @Min(0) @Max(10) int nps) {
}
