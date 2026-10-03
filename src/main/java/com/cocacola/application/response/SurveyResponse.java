package com.cocacola.application.response;

import com.cocacola.domain.model.Survey;
import java.time.Instant;

public record SurveyResponse(
        String id, String eventId, String participantId, int organization, int service, int experiences, int products,
        int overall, int nps, Instant createdAt) {

    public static SurveyResponse from(Survey s) {
        return new SurveyResponse(s.getId(), s.getEventId(), s.getParticipantId(), s.getOrganization(), s.getService(),
                s.getExperiences(), s.getProducts(), s.getOverall(), s.getNps(), s.getCreatedAt());
    }
}
