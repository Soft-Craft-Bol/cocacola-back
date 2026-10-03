package com.cocacola.application.response;

import com.cocacola.domain.model.Product;

public record ProductResponse(String id, String name, String category) {

    public static ProductResponse from(Product p) {
        return new ProductResponse(p.getId(), p.getName(), p.getCategory());
    }
}
