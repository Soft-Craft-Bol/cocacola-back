package com.cocacola.persistence.entity;

import com.cocacola.commons.enums.ActivityType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "activities", indexes = @Index(name = "idx_activity_event", columnList = "event_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivityEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String eventId;

    private String name;

    @Enumerated(EnumType.STRING)
    private ActivityType type;
    private String experienceId;
}
