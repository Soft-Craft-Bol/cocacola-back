package com.cocacola.application.rest.controller;

import com.cocacola.application.request.InteractionRequest;
import com.cocacola.application.response.InteractionResponse;
import com.cocacola.application.response.OkResponse;
import com.cocacola.domain.model.Interaction;
import com.cocacola.domain.service.InteractionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/interactions")
@RequiredArgsConstructor
public class InteractionController {

    private final InteractionService interactions;

    @GetMapping
    public List<InteractionResponse> list(@RequestParam(required = false) String eventId,
                                          @RequestParam(required = false) String participantId) {
        return interactions.list(eventId, participantId).stream().map(InteractionResponse::from).toList();
    }

    @PostMapping
    public InteractionResponse create(@Valid @RequestBody InteractionRequest r) {
        return InteractionResponse.from(interactions.create(Interaction.builder()
                .eventId(r.eventId()).participantId(r.participantId()).activityId(r.activityId()).type(r.type())
                .productId(r.productId()).rating(r.rating()).wouldBuy(r.wouldBuy()).wantsPromos(r.wantsPromos()).build()));
    }

    @DeleteMapping("/{id}")
    public OkResponse delete(@PathVariable String id) {
        interactions.delete(id);
        return OkResponse.done();
    }
}
