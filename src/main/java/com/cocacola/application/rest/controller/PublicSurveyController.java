package com.cocacola.application.rest.controller;

import com.cocacola.application.request.PublicSurveyRequest;
import com.cocacola.application.response.OkResponse;
import com.cocacola.domain.model.Survey;
import com.cocacola.domain.service.PublicSurveyService;
import com.cocacola.domain.service.PublicSurveyService.SurveyInfo;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Encuesta pública: el asistente la responde con el código de su QR (sin iniciar sesión). */
@RestController
@RequestMapping("/public/survey")
@RequiredArgsConstructor
public class PublicSurveyController {

    private final PublicSurveyService service;

    @GetMapping("/{code}")
    public SurveyInfo info(@PathVariable String code) {
        return service.info(code);
    }

    @PostMapping("/{code}")
    public OkResponse submit(@PathVariable String code, @Valid @RequestBody PublicSurveyRequest r) {
        service.submit(code, Survey.builder().organization(r.organization()).service(r.service())
                .experiences(r.experiences()).products(r.products()).overall(r.overall()).nps(r.nps()).build());
        return OkResponse.done();
    }
}
