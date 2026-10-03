package com.cocacola.application.rest.controller;

import com.cocacola.application.request.ParticipantRequest;
import com.cocacola.application.response.OkResponse;
import com.cocacola.application.response.ParticipantResponse;
import com.cocacola.domain.model.Participant;
import com.cocacola.domain.service.ParticipantService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/participants")
@RequiredArgsConstructor
public class ParticipantController {

    private final ParticipantService participants;

    public record ParticipantPage(List<ParticipantResponse> content, long totalElements, int totalPages, int number) {}

    @GetMapping("/page")
    public ParticipantPage page(@RequestParam String eventId, @RequestParam(defaultValue = "") String search,
                                @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        var result = participants.search(eventId, search, page, size);
        return new ParticipantPage(result.getContent().stream().map(ParticipantResponse::from).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber());
    }

    @GetMapping
    public List<ParticipantResponse> list(@RequestParam(required = false) String eventId) {
        return participants.list(eventId).stream().map(ParticipantResponse::from).toList();
    }

    @GetMapping("/by-code/{code}")
    public ParticipantResponse byCode(@PathVariable String code) {
        return ParticipantResponse.from(participants.getByCode(code));
    }

    @GetMapping("/{id}")
    public ParticipantResponse get(@PathVariable String id) {
        return ParticipantResponse.from(participants.get(id));
    }

    /** Publico: lo usa la pagina de registro del evento (QR). */
    @PostMapping
    public ParticipantResponse register(@Valid @RequestBody ParticipantRequest r) {
        return ParticipantResponse.from(participants.register(toDomain(r)));
    }

    @PutMapping("/{id}")
    public ParticipantResponse update(@PathVariable String id, @Valid @RequestBody ParticipantRequest r) {
        return ParticipantResponse.from(participants.update(id, toDomain(r)));
    }

    @PostMapping("/{id}/checkin")
    public ParticipantResponse checkIn(@PathVariable String id) {
        return ParticipantResponse.from(participants.checkIn(id));
    }

    @PostMapping("/{id}/checkout")
    public ParticipantResponse checkOut(@PathVariable String id) {
        return ParticipantResponse.from(participants.checkOut(id));
    }

    @DeleteMapping("/{id}")
    public OkResponse delete(@PathVariable String id) {
        participants.delete(id);
        return OkResponse.done();
    }

    private static Participant toDomain(ParticipantRequest r) {
        return Participant.builder().eventId(r.eventId()).firstName(r.firstName()).lastName(r.lastName())
                .phone(r.phone()).email(r.email()).city(r.city()).ageRange(r.ageRange()).preferences(r.preferences())
                .consent(Boolean.TRUE.equals(r.consent())).source(r.source()).build();
    }
}
