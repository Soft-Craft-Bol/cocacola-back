package com.cocacola.domain.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Survey {
    private String id;
    private String eventId;
    private String participantId;
    private int organization;
    private int service;
    private int experiences;
    private int products;
    private int overall;
    private int nps;
    private Instant createdAt;
}
