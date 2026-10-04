package com.cocacola.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "event_notes", indexes = @Index(name = "idx_event_notes_event", columnList = "event_id"))
@Getter @Setter
public class EventNoteEntity {
    @Id private String id;
    @Column(name = "event_id") private String eventId;
    private String authorId;
    private String authorName;
    private Instant createdAt;
    @Column(length = 2000) private String text;
}
