package com.cocacola.application.response;

import com.cocacola.domain.model.Participant;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record ParticipantResponse(
        String id, String eventId, String firstName, String lastName, String phone, String email, String city,
        String ageRange, @JsonProperty("isReturning") boolean isReturning, List<String> preferences, boolean consent,
        String source, String campaign, String qrCode, Instant registeredAt, Instant checkedInAt, Instant checkedOutAt) {

    public static ParticipantResponse from(Participant p) {
        return new ParticipantResponse(p.getId(), p.getEventId(), p.getFirstName(), p.getLastName(), p.getPhone(),
                p.getEmail(), p.getCity(), p.getAgeRange(), p.isReturning(), p.getPreferences(), p.isConsent(),
                p.getSource(), p.getCampaign(), p.getQrCode(), p.getRegisteredAt(), p.getCheckedInAt(), p.getCheckedOutAt());
    }
}
