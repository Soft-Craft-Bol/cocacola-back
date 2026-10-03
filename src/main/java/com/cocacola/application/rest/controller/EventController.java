package com.cocacola.application.rest.controller;

import com.cocacola.application.request.EventRequest;
import com.cocacola.application.response.EventResponse;
import com.cocacola.application.response.OkResponse;
import com.cocacola.domain.model.Event;
import com.cocacola.domain.model.ImageUpload;
import com.cocacola.domain.service.EventService;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService events;

    @GetMapping
    public List<EventResponse> list() {
        return events.list().stream().map(EventResponse::from).toList();
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable String id) {
        return EventResponse.from(events.get(id));
    }

    @PostMapping
    public EventResponse create(@Valid @RequestBody EventRequest r) {
        return EventResponse.from(events.create(toDomain(r)));
    }

    @PutMapping("/{id}")
    public EventResponse update(@PathVariable String id, @Valid @RequestBody EventRequest r) {
        return EventResponse.from(events.update(id, toDomain(r)));
    }

    /** Sube (o reemplaza) la imagen del evento. El archivo va en el campo "file". */
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EventResponse uploadImage(@PathVariable String id, @RequestParam("file") MultipartFile file) throws IOException {
        return EventResponse.from(events.setImage(id,
                new ImageUpload(file.getBytes(), file.getOriginalFilename(), file.getContentType())));
    }

    @DeleteMapping("/{id}/image")
    public EventResponse removeImage(@PathVariable String id) {
        return EventResponse.from(events.removeImage(id));
    }

    @DeleteMapping("/{id}")
    public OkResponse delete(@PathVariable String id) {
        events.delete(id);
        return OkResponse.done();
    }

    private static Event toDomain(EventRequest r) {
        return Event.builder().name(r.name()).type(r.type()).date(r.date()).location(r.location())
                .organizer(r.organizer()).manager(r.manager()).description(r.description()).objective(r.objective())
                .campaign(r.campaign()).budget(r.budget()).expected(r.expected()).channel(r.channel())
                .productIds(r.productIds()).status(r.status()).build();
    }
}
