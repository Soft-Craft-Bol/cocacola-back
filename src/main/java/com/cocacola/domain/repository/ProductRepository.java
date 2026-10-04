package com.cocacola.domain.repository;

import com.cocacola.domain.model.Product;
import java.util.List;
import java.util.Optional;

public interface ProductRepository {

    List<Product> findAll();
    Optional<Product> findById(String id);
    Product save(Product product);
    long count();
}
