package com.cocacola.domain.service;

import com.cocacola.commons.enums.EventStatus;
import com.cocacola.domain.helpers.IdGenerator;
import com.cocacola.domain.helpers.NotFoundException;
import com.cocacola.domain.model.ImageUpload;
import com.cocacola.domain.model.StoredImage;
import com.cocacola.domain.repository.ImageStorage;
import com.cocacola.domain.model.Event;
import com.cocacola.domain.repository.ActivityRepository;
import com.cocacola.domain.repository.CouponRepository;
import com.cocacola.domain.repository.EventRepository;
import com.cocacola.domain.repository.InteractionRepository;
import com.cocacola.domain.repository.ParticipantRepository;
import com.cocacola.domain.repository.SurveyRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final String IMAGE_FOLDER = "cocacola/events";

    private final EventRepository events;
    private final ActivityRepository activities;
    private final CouponRepository coupons;
    private final ParticipantRepository participants;
    private final InteractionRepository interactions;
    private final SurveyRepository surveys;
    private final ImageStorage images;
    private final EventAccessService access;
    private final com.cocacola.persistence.crud.EventOperationsRepository operations;
    private final com.cocacola.persistence.crud.EventNoteRepository notes;
    private final com.cocacola.domain.repository.ProductRepository products;
    private final com.cocacola.persistence.crud.ExperienceRepository experiences;

    public List<Event> list() {
        var scope = access.assignedEventIds();
        return events.findAll().stream().filter(e -> scope == null || scope.contains(e.getId())).sorted(Comparator.comparing(Event::getDate).reversed()).toList();
    }

    public Event get(String id) {
        access.require(id);
        return events.findById(id).orElseThrow(() -> new NotFoundException("Evento"));
    }

    public Event create(Event data) {
        data.setId(IdGenerator.newId());
        normalize(data);
        validateCatalog(data, null);
        return events.save(data);
    }

    public Event update(String id, Event data) {
        Event current = get(id);
        // Los clientes anteriores no enviaban experiencias: conservarlas al editar.
        if (data.getExperienceIds() == null) data.setExperienceIds(current.getExperienceIds());
        data.setId(id);
        // La imagen se administra solo con sus endpoints: se conserva al editar los datos
        data.setImageUrl(current.getImageUrl());
        data.setImagePublicId(current.getImagePublicId());
        if (operations.existsById(id)) {
            data.setOrganizer(current.getOrganizer());
            data.setManager(current.getManager());
        }
        normalize(data);
        validateCatalog(data, current);
        return events.save(data);
    }

    /** Sube la imagen del evento y elimina la anterior (si existia). */
    public Event setImage(String id, ImageUpload upload) {
        validate(upload);
        Event event = get(id);
        String previous = event.getImagePublicId();
        StoredImage stored = images.upload(upload, IMAGE_FOLDER);
        event.setImageUrl(stored.url());
        event.setImagePublicId(stored.publicId());
        Event saved;
        try {
            saved = events.save(event);
        } catch (RuntimeException ex) {
            discard(stored.publicId()); // no dejar huerfana la imagen recien subida
            throw ex;
        }
        discard(previous);
        return saved;
    }

    public Event removeImage(String id) {
        Event event = get(id);
        String previous = event.getImagePublicId();
        event.setImageUrl(null);
        event.setImagePublicId(null);
        Event saved = events.save(event);
        discard(previous);
        return saved;
    }

    @Transactional
    public void delete(String id) {
        notes.deleteByEventId(id);
        operations.deleteById(id);
        String imageId = events.findById(id).map(Event::getImagePublicId).orElse(null);
        interactions.deleteByEventId(id);
        coupons.deleteByEventId(id);
        surveys.deleteByEventId(id);
        participants.deleteByEventId(id);
        activities.deleteByEventId(id);
        events.deleteById(id);
        discard(imageId);
    }

    private void validate(ImageUpload upload) {
        if (upload == null || upload.content() == null || upload.content().length == 0) {
            throw new IllegalArgumentException("Selecciona una imagen");
        }
        if (upload.contentType() == null || !IMAGE_TYPES.contains(upload.contentType().toLowerCase())) {
            throw new IllegalArgumentException("Formato no permitido: usa JPG, PNG, WEBP o GIF");
        }
        if (upload.content().length > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException("La imagen supera el máximo de 5 MB");
        }
    }

    /** Elimina del almacenamiento; un fallo aqui no debe romper la operacion principal. */
    private void discard(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            images.delete(publicId);
        } catch (RuntimeException ex) {
            log.warn("No se pudo eliminar la imagen {} de Cloudinary: {}", publicId, ex.getMessage());
        }
    }

    private void normalize(Event e) {
        if (e.getStatus() == null) e.setStatus(EventStatus.PLANNED);
        if (e.getProductIds() == null) e.setProductIds(new ArrayList<>());
        if (e.getExperienceIds() == null) e.setExperienceIds(new ArrayList<>());
        if (e.getBudget() == null) e.setBudget(0L);
        if (e.getExpected() == null) e.setExpected(0);
    }

    private void validateCatalog(Event data, Event previous) {
        data.setProductIds(data.getProductIds().stream().distinct().toList());
        data.setExperienceIds(data.getExperienceIds().stream().distinct().toList());
        for (String id : data.getProductIds()) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Producto inválido");
            var product = products.findById(id).orElseThrow(() -> new IllegalArgumentException("El producto seleccionado no existe"));
            if (Boolean.TRUE.equals(product.getArchived()) && (previous == null || previous.getProductIds() == null || !previous.getProductIds().contains(id)))
                throw new IllegalArgumentException("No puedes agregar productos archivados a un evento");
        }
        for (String id : data.getExperienceIds()) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Experiencia inválida");
            var experience = experiences.findById(id).orElseThrow(() -> new IllegalArgumentException("La experiencia seleccionada no existe"));
            if (Boolean.TRUE.equals(experience.getArchived()) && (previous == null || previous.getExperienceIds() == null || !previous.getExperienceIds().contains(id)))
                throw new IllegalArgumentException("No puedes agregar experiencias archivadas a un evento");
        }
        if (previous != null) {
            boolean inUse = activities.findByEventId(previous.getId()).stream().anyMatch(a -> a.getExperienceId() != null && !data.getExperienceIds().contains(a.getExperienceId()));
            if (inUse) throw new IllegalArgumentException("No puedes quitar una experiencia que tiene actividades vinculadas");
        }
    }
}
