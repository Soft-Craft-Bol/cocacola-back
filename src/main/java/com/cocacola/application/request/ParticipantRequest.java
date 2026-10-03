package com.cocacola.application.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record ParticipantRequest(
        @NotBlank String eventId,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String phone,
        @NotBlank @Email String email,
        String city,
        String ageRange,
        List<String> preferences,
        Boolean consent,
        String source) {
}
