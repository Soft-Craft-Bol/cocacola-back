package com.cocacola.domain.service;

import com.cocacola.commons.Constants;
import com.cocacola.domain.helpers.ConflictException;
import com.cocacola.domain.helpers.IdGenerator;
import com.cocacola.domain.helpers.NotFoundException;
import com.cocacola.domain.model.Event;
import com.cocacola.domain.model.Participant;
import com.cocacola.domain.repository.EventRepository;
import com.cocacola.domain.repository.InteractionRepository;
import com.cocacola.domain.repository.ParticipantRepository;
import com.cocacola.domain.repository.SurveyRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ParticipantService {

    private final ParticipantRepository participants;
    private final EventRepository events;
    private final InteractionRepository interactions;
    private final SurveyRepository surveys;
    private final NotificationService notifications;
    private final CommunicationService communications;
    private final CrmService crm;
    private final EventAccessService access;
    private final EventOperationsService operations;

    public List<Participant> list(String eventId) {
        List<Participant> all = eventId == null || eventId.isBlank() ? participants.findAll() : participants.findByEventId(eventId);
        if (eventId != null && !eventId.isBlank()) access.require(eventId);
        var scope = access.assignedEventIds();
        return all.stream().filter(p -> scope == null || scope.contains(p.getEventId())).sorted(Comparator.comparing(Participant::getRegisteredAt).reversed()).toList();
    }

    public org.springframework.data.domain.Page<Participant> search(String eventId, String search, int page, int size) {
        access.require(eventId);
        return participants.search(eventId, search, Math.max(0, page), Math.max(1, Math.min(100, size)));
    }

    public Participant get(String id) {
        Participant p = participants.findById(id).orElseThrow(() -> new NotFoundException("Participante"));
        access.require(p.getEventId());
        return p;
    }

    public Participant getByCode(String code) {
        Participant p = participants.findByQrCode(code.trim().toUpperCase()).orElseThrow(() -> new NotFoundException("Código QR"));
        access.require(p.getEventId());
        return p;
    }

    @Transactional
    public Participant register(Participant data) {
        access.require(data.getEventId());
        Event event = events.findById(data.getEventId()).orElseThrow(() -> new NotFoundException("Evento"));
        String email = data.getEmail().trim().toLowerCase();
        if (participants.existsByEventIdAndEmail(event.getId(), email)) {
            throw new ConflictException("Este correo ya está registrado en el evento");
        }
        data.setId(IdGenerator.newId());
        data.setEmail(email);
        // Recurrente: la misma persona (correo o celular) ya ASISTIÓ a otro evento
        data.setReturning(hasAttendedBefore(email, data.getPhone(), event.getId()));
        data.setCampaign(event.getCampaign());
        data.setQrCode(newQrCode());
        data.setRegisteredAt(Instant.now());
        data.setCheckedInAt(null);
        data.setCheckedOutAt(null);
        if (data.getPreferences() == null) data.setPreferences(new ArrayList<>());
        Participant saved = participants.save(data);
        notifyRegistrationGoal(event);
        communications.onRegistered(saved, event);
        crm.onRegistered(saved, event);
        return saved;
    }

    public Participant update(String id, Participant data) {
        Participant p = get(id);
        p.setFirstName(data.getFirstName());
        p.setLastName(data.getLastName());
        p.setPhone(data.getPhone());
        p.setCity(data.getCity());
        p.setAgeRange(data.getAgeRange());
        p.setConsent(data.isConsent());
        p.setSource(data.getSource());
        if (data.getPreferences() != null) p.setPreferences(data.getPreferences());
        return participants.save(p);
    }

    public Participant checkIn(String id) {
        Participant p = get(id);
        if (p.getCheckedInAt() != null) throw new ConflictException("Este participante ya registró su ingreso");
        p.setCheckedInAt(Instant.now());
        // Al ingresar se confirma si es recurrente: puede haber asistido a otro evento después de inscribirse
        p.setReturning(hasAttendedBefore(p.getEmail(), p.getPhone(), p.getEventId()));
        Participant saved = participants.save(p);
        notifyAttendanceGoal(p.getEventId());
        return saved;
    }

    public Participant checkOut(String id) {
        Participant p = get(id);
        if (p.getCheckedInAt() == null) throw new ConflictException("El participante no ha registrado su ingreso");
        p.setCheckedOutAt(Instant.now());
        return participants.save(p);
    }

    @Transactional
    public void delete(String id) {
        interactions.deleteByParticipantId(id);
        surveys.deleteByParticipantId(id);
        participants.deleteById(id);
    }

    /** Avisa cuando los registros llegan al 50 % y al 100 % de los participantes esperados. */
    private void notifyRegistrationGoal(Event event) {
        int expected = operations.settings(event.getId()).registrationGoal();
        if (expected <= 0) return;
        int registered = participants.findByEventId(event.getId()).size();
        if (registered == expected) {
            notifications.notify("META_REGISTRO", "success", "Meta de registro alcanzada",
                    event.getName() + ": se registraron los " + expected + " participantes esperados.", event.getId());
        } else if (registered == (int) Math.ceil(expected / 2.0)) {
            notifications.notify("META_REGISTRO", "info", "Mitad de la meta de registro",
                    event.getName() + ": " + registered + " de " + expected + " participantes esperados registrados.", event.getId());
        }
    }

    /** Avisa cuando la asistencia llega al 50 % y al 100 % del aforo esperado. */
    private void notifyAttendanceGoal(String eventId) {
        Event event = events.findById(eventId).orElse(null);
        if (event == null) return;
        long attended = participants.findByEventId(eventId).stream().filter(x -> x.getCheckedInAt() != null).count();
        int expected = operations.settings(eventId).attendanceGoal();
        if (expected <= 0) return;
        if (attended == expected) {
            notifications.notify("META_ASISTENCIA", "success", "Aforo esperado alcanzado",
                    event.getName() + ": ya ingresaron " + expected + " asistentes.", eventId);
        } else if (attended == (long) Math.ceil(expected / 2.0)) {
            notifications.notify("META_ASISTENCIA", "info", "Mitad del aforo esperado",
                    event.getName() + ": han ingresado " + attended + " de " + expected + " asistentes esperados.", eventId);
        }
    }

    /** ¿La misma persona (por correo o por los últimos 10 dígitos del celular) asistió a otro evento? */
    private boolean hasAttendedBefore(String email, String phone, String eventId) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() > 10) digits = digits.substring(digits.length() - 10);
        String phoneDigits = digits.length() >= 7 ? digits : null;
        return participants.findByContact(email, phoneDigits).stream()
                .anyMatch(p -> p.getCheckedInAt() != null && !eventId.equals(p.getEventId()));
    }

    private String newQrCode() {
        String code;
        do {
            code = Constants.QR_PREFIX + IdGenerator.shortCode(8);
        } while (participants.existsByQrCode(code));
        return code;
    }
}
