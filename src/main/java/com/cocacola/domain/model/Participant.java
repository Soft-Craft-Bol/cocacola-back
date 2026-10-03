package com.cocacola.domain.model;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Participant {
    private String id;
    private String eventId;
    private String firstName;
    private String lastName;
    private String phone;
    private String email;
    private String city;
    private String ageRange;
    private boolean returning;
    private List<String> preferences;
    private boolean consent;
    private String source;
    private String campaign;
    private String qrCode;
    private Instant registeredAt;
    private Instant checkedInAt;
    private Instant checkedOutAt;
}
