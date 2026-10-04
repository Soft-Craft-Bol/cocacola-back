package com.cocacola.application.rest.controller;

import com.cocacola.application.request.CouponRequest;
import com.cocacola.application.response.CouponResponse;
import com.cocacola.application.response.OkResponse;
import com.cocacola.domain.service.CouponService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService coupons;

    @GetMapping
    public List<CouponResponse> list(@RequestParam(required = false) String eventId,
                                     @RequestParam(required = false) String participantId) {
        return coupons.list(eventId, participantId).stream().map(CouponResponse::from).toList();
    }

    @PostMapping
    public CouponResponse issue(@Valid @RequestBody CouponRequest.Issue r) {
        return CouponResponse.from(coupons.issue(r.eventId(), r.participantId(), r.benefit(), r.validUntil()));
    }

    @PostMapping("/redeem")
    public CouponResponse redeem(@Valid @RequestBody CouponRequest.Redeem r) {
        return CouponResponse.from(coupons.redeem(r.eventId(), r.participantId(), r.code(), r.activityId()));
    }

    @DeleteMapping("/{id}")
    public OkResponse delete(@PathVariable String id) {
        coupons.delete(id);
        return OkResponse.done();
    }
}
