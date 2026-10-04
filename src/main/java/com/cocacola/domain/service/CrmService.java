package com.cocacola.domain.service;

import com.cocacola.domain.model.Event;
import com.cocacola.domain.model.Participant;
import com.cocacola.domain.repository.CrmGateway;
import com.cocacola.domain.repository.EventRepository;
import com.cocacola.domain.repository.ParticipantRepository;
import com.cocacola.domain.repository.ProductRepository;
import com.cocacola.domain.model.Product;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** Integración con CRM: exportación de contactos (CSV) y envío por webhook. Solo contactos con consentimiento. */
@Slf4j
@Service
@RequiredArgsConstructor
public class CrmService {

    public record SyncResult(int total, int sent, int errors) {
    }

    public static final List<String> COLUMNS = List.of("nombre", "apellido", "correo", "celular", "ciudad", "rango_edad",
            "productos_interes", "evento", "campana", "fecha_registro", "tipo_participante", "fuente_registro");

    private final ParticipantRepository participants;
    private final EventRepository events;
    private final ProductRepository products;
    private final CrmGateway crm;

    @Value("${app.timezone:America/La_Paz}")
    private String timezone;

    public boolean configured() {
        return crm.isConfigured();
    }

    /** Contactos con consentimiento (de un evento o de todos). */
    public List<Map<String, Object>> contacts(String eventId) {
        Map<String, Event> eventById = events.findAll().stream().collect(Collectors.toMap(Event::getId, e -> e));
        Map<String, String> productNames = products.findAll().stream().collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));
        List<Participant> list = eventId == null || eventId.isBlank() ? participants.findAll() : participants.findByEventId(eventId);
        return list.stream().filter(Participant::isConsent).map(p -> contact(p, eventById.get(p.getEventId()), productNames)).toList();
    }

    public SyncResult sync(String eventId) {
        List<Map<String, Object>> list = contacts(eventId);
        int sent = 0, errors = 0;
        for (Map<String, Object> c : list) {
            try {
                crm.push(c);
                sent++;
            } catch (RuntimeException ex) {
                errors++;
                log.warn("No se pudo enviar un contacto al CRM: {}", ex.getMessage());
            }
        }
        return new SyncResult(list.size(), sent, errors);
    }

    /** Envío automático al registrarse (asíncrono) si el CRM está configurado y la persona dio consentimiento. */
    @Async
    public void onRegistered(Participant p, Event event) {
        if (!crm.isConfigured() || !p.isConsent()) return;
        try {
            Map<String, String> names = products.findAll().stream().collect(Collectors.toMap(Product::getId, Product::getName, (a, b) -> a));
            crm.push(contact(p, event, names));
        } catch (RuntimeException ex) {
            log.warn("No se pudo enviar el contacto {} al CRM: {}", p.getId(), ex.getMessage());
        }
    }

    private Map<String, Object> contact(Participant p, Event e, Map<String, String> productNames) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nombre", p.getFirstName());
        m.put("apellido", p.getLastName());
        m.put("correo", p.getEmail());
        m.put("celular", p.getPhone());
        m.put("ciudad", p.getCity());
        m.put("rango_edad", p.getAgeRange());
        m.put("productos_interes", p.getPreferences() == null ? "" : p.getPreferences().stream()
                .map(id -> productNames.getOrDefault(id, id)).collect(Collectors.joining(", ")));
        m.put("evento", e == null ? "" : e.getName());
        m.put("campana", p.getCampaign());
        m.put("fecha_registro", p.getRegisteredAt() == null ? null : p.getRegisteredAt().atZone(ZoneId.of(timezone)).toLocalDate().toString());
        m.put("tipo_participante", p.isReturning() ? "Recurrente" : "Nuevo");
        m.put("fuente_registro", p.getSource());
        return m;
    }
}
