package com.cocacola.application.response;

import com.cocacola.domain.model.Product;

public record ProductResponse(String id, String name, String category, String flavor, String presentation, boolean archived) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getCategory(), p.getFlavor(), p.getPresentation(), Boolean.TRUE.equals(p.getArchived()));
    }
}
