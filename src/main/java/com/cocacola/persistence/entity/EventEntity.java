package com.cocacola.persistence.entity;

import com.cocacola.commons.enums.EventStatus;
import java.time.Instant;
import java.util.List;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEntity {

    @Id
    private String id;

    private String name;

    private String type;

    @Column(name = "event_date")
    private Instant date;

    private String location;

    private String organizer;

    private String manager;

    @Column(length = 2000)
    private String description;

    @Column(length = 2000)
    private String objective;

    private String campaign;

    private Long budget;

    private Integer expected;

    private String channel;

    @ElementCollection(fetch = FetchType.LAZY)
    @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
    @CollectionTable(name = "event_products", joinColumns = @JoinColumn(name = "event_id"),
        indexes = @Index(name = "idx_event_products_event", columnList = "event_id"))
    @Column(name = "product_id")
    private List<String> productIds;

    @ElementCollection(fetch = FetchType.LAZY)
    @org.hibernate.annotations.Fetch(org.hibernate.annotations.FetchMode.SUBSELECT)
    @CollectionTable(name = "event_experiences", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "experience_id")
    private List<String> experienceIds;

    @Enumerated(EnumType.STRING)
    private EventStatus status;

    private String imageUrl;

    private String imagePublicId;
}
