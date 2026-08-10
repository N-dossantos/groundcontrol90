package com.ecommerce.repository;

import com.ecommerce.entity.ProductoVariante;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductoVarianteRepository extends JpaRepository<ProductoVariante, Long> {
    List<ProductoVariante> findByProductoId(Long productoId);
}
