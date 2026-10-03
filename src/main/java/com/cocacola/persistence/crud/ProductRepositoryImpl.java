package com.cocacola.persistence.crud;

import com.cocacola.domain.model.Product;
import com.cocacola.domain.repository.ProductRepository;
import com.cocacola.persistence.mapper.ProductMapper;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final ProductCrudRepository crud;
    private final ProductMapper mapper;

    @Override
    public List<Product> findAll() {
        return crud.findAll().stream().map(mapper::toDomain).toList();
    }

    @Override
    public Product save(Product product) {
        return mapper.toDomain(crud.save(mapper.toEntity(product)));
    }

    @Override
    public long count() {
        return crud.count();
    }
}
