package com.cocacola.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "message_log", indexes = {
        @Index(name = "idx_message_event", columnList = "event_id"),
        @Index(name = "idx_message_participant", columnList = "participant_id")})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageLogEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String participantId;

    private String channel;

    private String type;

    private String status;

    @Column(length = 500)
    private String detail;

    private Instant sentAt;
}
