package com.cocacola.persistence.crud;

import com.cocacola.persistence.entity.ProductEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCrudRepository extends JpaRepository<ProductEntity, String> {

}
