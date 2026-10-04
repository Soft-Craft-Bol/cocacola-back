package com.cocacola.domain.model;

import com.cocacola.commons.enums.ActivityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Activity {
    private String id;
    private String eventId;
    private String name;
    private ActivityType type;
    private String experienceId;
}
