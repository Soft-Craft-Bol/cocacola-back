package com.cocacola.persistence.mapper;

import com.cocacola.domain.model.Product;
import com.cocacola.persistence.entity.ProductEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    Product toDomain(ProductEntity entity);

    ProductEntity toEntity(Product domain);
}
