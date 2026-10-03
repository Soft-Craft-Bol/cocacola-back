package com.cocacola.persistence.entity;

import com.cocacola.commons.enums.InteractionType;
import java.time.Instant;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "interactions", indexes = @Index(name = "idx_interaction_event", columnList = "event_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InteractionEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String participantId;

    private String activityId;

    @Enumerated(EnumType.STRING)
    private InteractionType type;

    private String productId;

    private Integer rating;

    private Boolean wouldBuy;

    private Boolean wantsPromos;

    @Column(name = "occurred_at")
    private Instant at;
}
