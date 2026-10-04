package com.cocacola.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Cupón de un beneficio emitido a un participante. Es de un solo uso y puede tener vigencia. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon {
    private String id;
    private String eventId;
    private String participantId;
    private String code;
    private String benefit;
    private Instant validUntil;
    private Instant issuedAt;
    private Instant redeemedAt;
    private String redeemActivityId;
    private String redeemInteractionId;
}
