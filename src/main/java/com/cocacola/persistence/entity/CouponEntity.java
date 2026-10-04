package com.cocacola.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "coupons", indexes = {
        @Index(name = "idx_coupon_event", columnList = "event_id"),
        @Index(name = "idx_coupon_participant", columnList = "participant_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String participantId;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String benefit;

    private Instant validUntil;

    @Column(nullable = false)
    private Instant issuedAt;

    private Instant redeemedAt;

    private String redeemActivityId;

    private String redeemInteractionId;
}
