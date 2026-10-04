package com.cocacola.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "event_operations")
@Getter @Setter
public class EventOperationsEntity {
    @Id
    private String eventId;
    private String organizerUserId;
    private String managerUserId;
    private Integer registrationGoal;
    private Integer attendanceGoal;
    private Integer conversionGoal;
    private Integer lowNpsThreshold;
}
