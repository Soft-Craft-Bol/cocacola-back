package com.cocacola.application.rest.controller;

import com.cocacola.domain.service.ExperienceService;
import com.cocacola.persistence.entity.ExperienceEntity;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/experiences") @RequiredArgsConstructor
public class ExperienceController {
    private final ExperienceService experiences;
    @GetMapping public List<ExperienceEntity> list() { return experiences.list(); }
    @PostMapping public ExperienceEntity create(@Valid @RequestBody ExperienceService.Input r) { return experiences.save(null, r); }
    @PutMapping("/{id}") public ExperienceEntity update(@PathVariable String id, @Valid @RequestBody ExperienceService.Input r) { return experiences.save(id, r); }
}
