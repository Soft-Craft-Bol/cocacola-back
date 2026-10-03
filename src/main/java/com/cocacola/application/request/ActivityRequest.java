package com.cocacola.application.request;

import com.cocacola.commons.enums.ActivityType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ActivityRequest(@NotBlank String eventId, @NotBlank String name, @NotNull ActivityType type) {
}
