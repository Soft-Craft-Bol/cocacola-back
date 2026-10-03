package com.cocacola.commons.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

public enum ActivityType {
    TASTING("tasting"),
    EXPERIENCE("experience"),
    CONTEST("contest"),
    PROMO("promo"),
    PHOTOCALL("photocall"),
    SURVEY("survey"),
    REDEEM("redeem"),
    CONTENT("content");

    private final String value;

    ActivityType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static ActivityType fromValue(String value) {
        return Arrays.stream(values())
                .filter(e -> e.value.equalsIgnoreCase(value) || e.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Valor inválido para ActivityType: " + value));
    }
}
