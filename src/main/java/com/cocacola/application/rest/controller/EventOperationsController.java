package com.cocacola.application.rest.controller;

import com.cocacola.domain.service.EventOperationsService;
import com.cocacola.domain.service.EventOperationsService.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/events/{id}") @RequiredArgsConstructor
public class EventOperationsController {
    private final EventOperationsService operations;
    public record NoteRequest(@NotBlank @Size(max = 2000) String text) {}
    @GetMapping("/operations") public Overview get(@PathVariable String id) { return operations.get(id); }
    @PutMapping("/operations") public Overview save(@PathVariable String id, @Valid @RequestBody Settings settings) {
        return operations.save(id, settings);
    }
    @GetMapping("/notes") public List<Note> notes(@PathVariable String id) { return operations.notes(id); }
    @PostMapping("/notes") public Note add(@PathVariable String id, @Valid @RequestBody NoteRequest note) {
        return operations.addNote(id, note.text());
    }
}
