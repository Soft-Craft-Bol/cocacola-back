package com.cocacola.application.request;

import com.cocacola.commons.enums.Channel;
import com.cocacola.commons.enums.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SendCommunicationRequest(
        @NotBlank String eventId,
        @NotNull MessageType type,
        @NotNull Channel channel,
        String audience,
        String targetEventId) {
}
