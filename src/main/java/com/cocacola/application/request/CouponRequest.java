package com.cocacola.application.request;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class CouponRequest {

    private CouponRequest() {
    }

    public record Issue(@NotBlank String eventId, @NotBlank String participantId, @NotBlank String benefit, Instant validUntil) {
    }

    public record Redeem(@NotBlank String eventId, @NotBlank String participantId, @NotBlank String code, String activityId) {
    }
}
