package com.cocacola.domain.service;

import com.cocacola.domain.model.Product;
import com.cocacola.domain.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository products;
    private final EventAccessService access;

    public List<Product> list() {
        return products.findAll().stream().sorted(java.util.Comparator.comparing(Product::displayName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public Product save(String id, com.cocacola.application.request.ProductRequest r) {
        access.requireRole("ADMIN");
        var p = id == null ? new Product() : products.findById(id)
                .orElseThrow(() -> new com.cocacola.domain.helpers.NotFoundException("Producto"));
        String name = r.name().trim(), category = r.category().trim();
        String flavor = r.flavor() == null ? "" : r.flavor().trim();
        String presentation = r.presentation() == null ? "" : r.presentation().trim();
        if (products.findAll().stream().anyMatch(other -> !java.util.Objects.equals(id, other.getId())
                && name.equalsIgnoreCase(other.getName()) && category.equalsIgnoreCase(other.getCategory())
                && flavor.equalsIgnoreCase(other.getFlavor() == null ? "" : other.getFlavor())
                && presentation.equalsIgnoreCase(other.getPresentation() == null ? "" : other.getPresentation())))
            throw new com.cocacola.domain.helpers.ConflictException("Ya existe ese producto con el mismo sabor y presentación; puedes editarlo o reactivarlo");
        if (id == null) p.setId(com.cocacola.domain.helpers.IdGenerator.newId());
        p.setName(name); p.setCategory(category); p.setFlavor(flavor); p.setPresentation(presentation); p.setArchived(r.archived());
        return products.save(p);
    }
}
