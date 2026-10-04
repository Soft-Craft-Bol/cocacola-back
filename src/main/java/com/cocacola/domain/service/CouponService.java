package com.cocacola.domain.service;

import com.cocacola.commons.enums.InteractionType;
import com.cocacola.domain.helpers.ConflictException;
import com.cocacola.domain.helpers.IdGenerator;
import com.cocacola.domain.helpers.NotFoundException;
import com.cocacola.domain.model.Activity;
import com.cocacola.domain.model.Coupon;
import com.cocacola.domain.model.Interaction;
import com.cocacola.domain.model.Participant;
import com.cocacola.domain.repository.ActivityRepository;
import com.cocacola.domain.repository.CouponRepository;
import com.cocacola.domain.repository.InteractionRepository;
import com.cocacola.domain.repository.ParticipantRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Emisión y canje de cupones. Cada canje válido registra una interacción de tipo REDEEM. */
@Service
@RequiredArgsConstructor
public class CouponService {

    /** Cupón con los datos del participante que lo recibió (el participante puede haber sido eliminado). */
    public record Detail(Coupon coupon, Participant participant) {
    }

    private final CouponRepository coupons;
    private final ParticipantRepository participants;
    private final ActivityRepository activities;
    private final InteractionRepository interactions;
    private final EventAccessService access;

    public List<Detail> list(String eventId, String participantId) {
        if (eventId != null && !eventId.isBlank()) access.require(eventId);
        List<Coupon> all;
        if (participantId != null && !participantId.isBlank()) {
            access.require(participants.findById(participantId).orElseThrow(() -> new NotFoundException("Participante")).getEventId());
            all = coupons.findByParticipantId(participantId);
        } else if (eventId != null && !eventId.isBlank()) {
            all = coupons.findByEventId(eventId);
        } else {
            all = coupons.findAll();
        }
        var scope = access.assignedEventIds();
        return all.stream()
                .filter(c -> scope == null || scope.contains(c.getEventId()))
                .filter(c -> eventId == null || eventId.isBlank() || eventId.equals(c.getEventId()))
                .sorted(Comparator.comparing(Coupon::getIssuedAt).reversed())
                .map(c -> new Detail(c, participants.findById(c.getParticipantId()).orElse(null)))
                .toList();
    }

    public Detail issue(String eventId, String participantId, String benefit, Instant validUntil) {
        access.require(eventId);
        Participant participant = participantOf(eventId, participantId);
        if (participant.getCheckedInAt() == null) {
            throw new ConflictException("El participante debe registrar su ingreso antes de recibir un cupón");
        }
        if (validUntil != null && !validUntil.isAfter(Instant.now())) {
            throw new IllegalArgumentException("La vigencia del cupón debe ser una fecha futura");
        }
        Coupon coupon = Coupon.builder().id(IdGenerator.newId()).eventId(eventId).participantId(participantId)
                .code(newCode()).benefit(benefit.trim()).validUntil(validUntil).issuedAt(Instant.now()).build();
        return new Detail(coupons.save(coupon), participant);
    }

    @Transactional
    public Detail redeem(String eventId, String participantId, String code, String activityId) {
        access.require(eventId);
        Coupon coupon = coupons.findByCode(code.trim().toUpperCase()).orElseThrow(() -> new NotFoundException("Cupón"));
        if (!coupon.getEventId().equals(eventId)) throw new IllegalArgumentException("El cupón pertenece a otro evento");
        // El cupón solo se canjea a nombre de su titular, aunque alguien más tenga el código
        if (!coupon.getParticipantId().equals(participantId)) throw new IllegalArgumentException("El cupón pertenece a otro participante");
        if (coupon.getRedeemedAt() != null) throw new ConflictException("Este cupón ya fue canjeado");
        Instant now = Instant.now();
        if (coupon.getValidUntil() != null && now.isAfter(coupon.getValidUntil())) throw new ConflictException("El cupón está vencido");

        Participant participant = participantOf(eventId, coupon.getParticipantId());
        if (participant.getCheckedInAt() == null) {
            throw new ConflictException("El participante debe registrar su ingreso antes de canjear");
        }
        String usedActivityId = null;
        if (activityId != null && !activityId.isBlank()) {
            Activity activity = activities.findById(activityId).orElseThrow(() -> new NotFoundException("Actividad"));
            if (!activity.getEventId().equals(eventId)) throw new IllegalArgumentException("La actividad pertenece a otro evento");
            usedActivityId = activityId;
        }

        Interaction redemption = interactions.save(Interaction.builder().id(IdGenerator.newId()).eventId(eventId)
                .participantId(participant.getId()).activityId(usedActivityId).type(InteractionType.REDEEM).at(now).build());
        coupon.setRedeemedAt(now);
        coupon.setRedeemActivityId(usedActivityId);
        coupon.setRedeemInteractionId(redemption.getId());
        return new Detail(coupons.save(coupon), participant);
    }

    public void delete(String id) {
        Coupon coupon = coupons.findById(id).orElseThrow(() -> new NotFoundException("Cupón"));
        access.require(coupon.getEventId());
        if (coupon.getRedeemedAt() != null) throw new ConflictException("Un cupón canjeado se conserva en el historial");
        coupons.deleteById(id);
    }

    private Participant participantOf(String eventId, String participantId) {
        Participant participant = participants.findById(participantId).orElseThrow(() -> new NotFoundException("Participante"));
        access.require(participant.getEventId());
        if (!participant.getEventId().equals(eventId)) throw new IllegalArgumentException("El participante pertenece a otro evento");
        return participant;
    }

    private String newCode() {
        String code;
        do {
            code = IdGenerator.shortCode(8);
        } while (coupons.existsByCode(code));
        return code;
    }
}
