package com.cocacola.domain.model;

import com.cocacola.commons.enums.InteractionType;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Interaction {
    private String id;
    private String eventId;
    private String participantId;
    private String activityId;
    private InteractionType type;
    private String productId;
    private Integer rating;
    private Boolean wouldBuy;
    private Boolean wantsPromos;
    private Instant at;
}
