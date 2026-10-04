package com.cocacola.domain.service;

import com.cocacola.domain.helpers.IdGenerator;
import com.cocacola.domain.helpers.NotFoundException;
import com.cocacola.domain.repository.EventRepository;
import com.cocacola.domain.repository.UserRepository;
import com.cocacola.commons.enums.Role;
import com.cocacola.persistence.crud.EventOperationsRepository;
import com.cocacola.persistence.crud.EventNoteRepository;
import com.cocacola.persistence.entity.EventOperationsEntity;
import com.cocacola.persistence.entity.EventNoteEntity;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class EventOperationsService {
    public record Settings(String organizerUserId, String managerUserId,
            @NotNull @Min(0) Integer registrationGoal, @NotNull @Min(0) Integer attendanceGoal,
            @NotNull @Min(0) Integer conversionGoal, @NotNull @Min(0) @Max(10) Integer lowNpsThreshold) {}
    public record Overview(Settings settings, String organizerName, String managerName, boolean configured,
            long registered, long attended, long conversions) {}
    public record Note(String id, String authorName, Instant createdAt, String text) {}
    private final EventOperationsRepository operations;
    private final EventNoteRepository notes;
    private final EventRepository events;
    private final UserRepository users;
    private final EventAccessService access;
    private final com.cocacola.domain.repository.ParticipantRepository participants;
    private final com.cocacola.domain.repository.InteractionRepository interactions;

    /** Valores anteriores para eventos que todavía no tienen configuración propia. */
    public Settings settings(String id) {
        var event = events.findById(id).orElseThrow(() -> new NotFoundException("Evento"));
        return operations.findById(id).map(o -> new Settings(o.getOrganizerUserId(), o.getManagerUserId(),
                o.getRegistrationGoal(), o.getAttendanceGoal(), o.getConversionGoal(), o.getLowNpsThreshold()))
                .orElseGet(() -> new Settings(null, null, event.getExpected() == null ? 0 : event.getExpected(),
                        event.getExpected() == null ? 0 : event.getExpected(), 10, 6));
    }

    public Overview get(String id) {
        access.requireRole("ADMIN", "ORGANIZER", "MARKETING");
        access.require(id);
        Settings s = settings(id);
        var people = participants.findByEventId(id);
        long conversions = interactions.findByEventId(id).stream()
                .filter(i -> i.getType() == com.cocacola.commons.enums.InteractionType.CONVERSION)
                .map(i -> i.getParticipantId()).distinct().count();
        return new Overview(s, name(s.organizerUserId()), name(s.managerUserId()), operations.existsById(id),
                people.size(), people.stream().filter(p -> p.getCheckedInAt() != null).count(), conversions);
    }

    @Transactional
    public Overview save(String id, Settings s) {
        access.requireRole("ADMIN");
        var event = events.findById(id).orElseThrow(() -> new NotFoundException("Evento"));
        var organizer = assignedUser(s.organizerUserId());
        var manager = assignedUser(s.managerUserId());
        var entity = new EventOperationsEntity();
        entity.setEventId(id);
        entity.setOrganizerUserId(organizer.getId());
        entity.setManagerUserId(manager.getId());
        entity.setRegistrationGoal(s.registrationGoal());
        entity.setAttendanceGoal(s.attendanceGoal());
        entity.setConversionGoal(s.conversionGoal());
        entity.setLowNpsThreshold(s.lowNpsThreshold());
        operations.save(entity);
        event.setOrganizer(organizer.getName());
        event.setManager(manager.getName());
        events.save(event);
        return get(id);
    }

    private com.cocacola.domain.model.User assignedUser(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Selecciona organizador y responsable");
        return users.findById(id).filter(u -> u.isActive() && (u.getRole() == Role.ADMIN || u.getRole() == Role.ORGANIZER))
                .orElseThrow(() -> new IllegalArgumentException("El responsable debe ser un administrador u organizador activo"));
    }

    private String name(String id) {
        return id == null ? null : users.findById(id).map(u -> u.getName()).orElse("Usuario no disponible");
    }

    public List<Note> notes(String id) {
        get(id);
        return notes.findByEventIdOrderByCreatedAtDesc(id).stream()
                .map(n -> new Note(n.getId(), n.getAuthorName(), n.getCreatedAt(), n.getText())).toList();
    }

    public Note addNote(String id, String text) {
        access.requireRole("ADMIN", "ORGANIZER");
        access.require(id);
        settings(id);
        if (text == null || text.isBlank() || text.length() > 2000) throw new IllegalArgumentException("Escribe una observación de hasta 2000 caracteres");
        var user = access.currentUser();
        var note = new EventNoteEntity();
        note.setId(IdGenerator.newId()); note.setEventId(id); note.setAuthorId(user.id());
        note.setAuthorName(name(user.id())); note.setCreatedAt(Instant.now()); note.setText(text.trim());
        notes.save(note);
        return new Note(note.getId(), note.getAuthorName(), note.getCreatedAt(), note.getText());
    }
}
