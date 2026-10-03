package com.cocacola.persistence.entity;

import java.time.Instant;
import java.util.List;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "participants", indexes = {
    @Index(name = "idx_participant_event", columnList = "event_id"),
    @Index(name = "idx_participant_event_registered", columnList = "event_id, registered_at DESC, id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParticipantEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String eventId;

    private String firstName;

    private String lastName;

    private String phone;

    @Column(nullable = false)
    private String email;

    private String city;

    private String ageRange;

    @Column(name = "is_returning")
    private boolean returning;

    @ElementCollection(fetch = FetchType.LAZY)
    @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
    @CollectionTable(name = "participant_preferences", joinColumns = @JoinColumn(name = "participant_id"),
        indexes = @Index(name = "idx_preferences_participant", columnList = "participant_id"))
    @Column(name = "product_id")
    private List<String> preferences;

    private boolean consent;

    private String source;

    private String campaign;

    @Column(nullable = false, unique = true)
    private String qrCode;

    private Instant registeredAt;

    private Instant checkedInAt;

    private Instant checkedOutAt;
}
