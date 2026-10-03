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

    public List<Product> list() {
        return products.findAll();
    }
}
