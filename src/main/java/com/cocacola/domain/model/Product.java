package com.cocacola.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    private String id;
    private String name;
    private String category;
    private String flavor;
    private String presentation;
    private Boolean archived;

    public Product(String id, String name, String category) {
        this(id, name, category, "", "", false);
    }

    public String displayName() {
        return java.util.stream.Stream.of(name, flavor, presentation).filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.joining(" · "));
    }
}
