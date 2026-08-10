package com.ecommerce.repository.spec;

import com.ecommerce.dto.ProductoFiltroDTO;
import com.ecommerce.entity.Producto;
import com.ecommerce.entity.ProductoVariante;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

/**
 * Combina los filtros opcionales del catálogo en una sola consulta, en vez de
 * multiplicar métodos de repositorio por cada combinación posible.
 */
public class ProductoSpecifications {

    private ProductoSpecifications() {}

    public static Specification<Producto> conFiltro(ProductoFiltroDTO filtro) {
        return (root, query, cb) -> {
            Predicate predicates = cb.conjunction();

            if (filtro.getClub() != null && !filtro.getClub().isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("club"), filtro.getClub()));
            }
            if (filtro.getLiga() != null && !filtro.getLiga().isBlank()) {
                predicates = cb.and(predicates, cb.equal(root.get("liga"), filtro.getLiga()));
            }
            if (filtro.getTipo() != null) {
                predicates = cb.and(predicates, cb.equal(root.get("tipo"), filtro.getTipo()));
            }
            if (filtro.getPrecioMin() != null) {
                predicates = cb.and(predicates,
                        cb.greaterThanOrEqualTo(root.get("price"), filtro.getPrecioMin()));
            }
            if (filtro.getPrecioMax() != null) {
                predicates = cb.and(predicates,
                        cb.lessThanOrEqualTo(root.get("price"), filtro.getPrecioMax()));
            }
            if (filtro.getTalle() != null && !filtro.getTalle().isBlank()) {
                // El join contra variantes puede duplicar filas del producto padre
                query.distinct(true);
                Join<Producto, ProductoVariante> variantes = root.join("variantes");
                predicates = cb.and(predicates, cb.equal(variantes.get("talle"), filtro.getTalle()));
            }

            return predicates;
        };
    }
}
