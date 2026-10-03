package com.cocacola.domain.model;

import com.cocacola.commons.enums.EventStatus;
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
public class Event {
    private String id;
    private String name;
    private String type;
    private Instant date;
    private String location;
    private String organizer;
    private String manager;
    private String description;
    private String objective;
    private String campaign;
    private Long budget;
    private Integer expected;
    private String channel;
    private List<String> productIds;
    private EventStatus status;
    private String imageUrl;
    private String imagePublicId;
}
