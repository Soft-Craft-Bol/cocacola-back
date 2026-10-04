package com.cocacola.application.response;

import com.cocacola.domain.model.Coupon;
import com.cocacola.domain.service.CouponService;
import java.time.Instant;

public record CouponResponse(
        String id, String eventId, String participantId, String participantName, String participantEmail, boolean consent,
        String code, String benefit, String status, Instant validUntil, Instant issuedAt, Instant redeemedAt,
        String redeemActivityId, String redeemInteractionId) {

    public static CouponResponse from(CouponService.Detail d) {
        Coupon c = d.coupon();
        var p = d.participant();
        return new CouponResponse(c.getId(), c.getEventId(), c.getParticipantId(),
                p == null ? null : p.getFirstName() + " " + p.getLastName(),
                p == null ? null : p.getEmail(),
                p != null && p.isConsent(),
                c.getCode(), c.getBenefit(), status(c), c.getValidUntil(), c.getIssuedAt(), c.getRedeemedAt(),
                c.getRedeemActivityId(), c.getRedeemInteractionId());
    }

    private static String status(Coupon c) {
        if (c.getRedeemedAt() != null) return "redeemed";
        if (c.getValidUntil() != null && Instant.now().isAfter(c.getValidUntil())) return "expired";
        return "issued";
    }
}
