package com.cocacola.persistence.crud;

import com.cocacola.persistence.entity.ExperienceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperienceRepository extends JpaRepository<ExperienceEntity, String> {}
