package com.cocacola.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="experiences") @Getter @Setter
public class ExperienceEntity {
    @Id private String id;
    private String name;
    private String category;
    @Column(length=2000) private String description;
    private Boolean archived;
}
