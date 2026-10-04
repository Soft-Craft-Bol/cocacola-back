package com.cocacola.domain.service;

import com.cocacola.domain.helpers.*;
import com.cocacola.persistence.crud.ExperienceRepository;
import com.cocacola.persistence.entity.ExperienceEntity;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class ExperienceService {
    public record Input(@NotBlank @Size(max=120) String name, @NotBlank @Size(max=100) String category,
            @Size(max=2000) String description, boolean archived) {}
    private final ExperienceRepository experiences;
    private final EventAccessService access;
    public List<ExperienceEntity> list() {
        return experiences.findAll().stream().sorted(java.util.Comparator.comparing(ExperienceEntity::getName, String.CASE_INSENSITIVE_ORDER)).toList();
    }
    public ExperienceEntity save(String id, Input r) {
        access.requireRole("ADMIN");
        var e = id == null ? new ExperienceEntity() : experiences.findById(id).orElseThrow(() -> new NotFoundException("Experiencia"));
        if (experiences.findAll().stream().anyMatch(other -> !java.util.Objects.equals(id, other.getId())
                && r.name().trim().equalsIgnoreCase(other.getName()) && r.category().trim().equalsIgnoreCase(other.getCategory())))
            throw new ConflictException("Ya existe esa experiencia; puedes editarla o reactivarla");
        if (id == null) e.setId(IdGenerator.newId());
        e.setName(r.name().trim()); e.setCategory(r.category().trim());
        e.setDescription(r.description() == null ? "" : r.description().trim()); e.setArchived(r.archived());
        return experiences.save(e);
    }
}
