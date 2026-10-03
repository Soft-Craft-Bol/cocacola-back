package com.cocacola.application.rest.controller;

import com.cocacola.application.request.SurveyRequest;
import com.cocacola.application.response.SurveyResponse;
import com.cocacola.domain.model.Survey;
import com.cocacola.domain.service.SurveyService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/surveys")
@RequiredArgsConstructor
public class SurveyController {

    private final SurveyService surveys;

    @GetMapping
    public List<SurveyResponse> list(@RequestParam(required = false) String eventId) {
        return surveys.list(eventId).stream().map(SurveyResponse::from).toList();
    }

    @PostMapping
    public SurveyResponse create(@Valid @RequestBody SurveyRequest r) {
        return SurveyResponse.from(surveys.create(Survey.builder().eventId(r.eventId()).participantId(r.participantId())
                .organization(r.organization()).service(r.service()).experiences(r.experiences())
                .products(r.products()).overall(r.overall()).nps(r.nps()).build()));
    }
}
