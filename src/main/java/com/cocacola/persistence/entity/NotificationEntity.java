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
@Table(name = "notifications", indexes = @Index(name = "idx_notification_created", columnList = "created_at"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEntity {

    @Id
    private String id;

    private String type;

    private String severity;

    private String title;

    @Column(length = 1000)
    private String message;

    private String eventId;

    private Instant createdAt;

    @Column(name = "is_read")
    private boolean read;
}
