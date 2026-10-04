package com.cocacola.application.rest.controller;

import com.cocacola.application.response.ProductResponse;
import com.cocacola.domain.service.ProductService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService products;

    @GetMapping
    public List<ProductResponse> list() {
        return products.list().stream().map(ProductResponse::from).toList();
    }

    @PostMapping
    public ProductResponse create(@Valid @RequestBody com.cocacola.application.request.ProductRequest r) {
        return ProductResponse.from(products.save(null, r));
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable String id, @Valid @RequestBody com.cocacola.application.request.ProductRequest r) {
        return ProductResponse.from(products.save(id, r));
    }
}
