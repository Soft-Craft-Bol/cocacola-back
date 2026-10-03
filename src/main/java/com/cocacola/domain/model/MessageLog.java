package com.cocacola.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageLog {
    private String id;
    private String eventId;
    private String participantId;
    private String channel;   // EMAIL | WHATSAPP
    private String type;      // CONFIRMATION, REMINDER, THANKS, INVITATION:<eventoId>
    private String status;    // SENT | ERROR
    private String detail;
    private Instant sentAt;
}
