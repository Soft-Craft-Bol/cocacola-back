package com.cocacola.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Aviso para el equipo responsable (meta alcanzada, NPS bajo, etc.). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    private String id;
    private String type;      // META_REGISTRO, META_ASISTENCIA, NPS_BAJO, CONVERSIONES...
    private String severity;  // info | success | warning
    private String title;
    private String message;
    private String eventId;
    private Instant createdAt;
    private boolean read;
}
