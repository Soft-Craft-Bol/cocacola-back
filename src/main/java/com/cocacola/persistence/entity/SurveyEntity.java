package com.cocacola.persistence.entity;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "surveys", indexes = @Index(name = "idx_survey_event", columnList = "event_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SurveyEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false, unique = true)
    private String participantId;

    private int organization;

    private int service;

    private int experiences;

    private int products;

    private int overall;

    private int nps;

    private Instant createdAt;
}
