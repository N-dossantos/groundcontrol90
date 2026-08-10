package com.ecommerce.dto;

import com.ecommerce.entity.ProductoVariante;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoVarianteDTO {
    private Long id;
    private String talle;
    private Integer stock;
    private String sku;

    public ProductoVarianteDTO(ProductoVariante variante) {
        this.id = variante.getId();
        this.talle = variante.getTalle();
        this.stock = variante.getStock();
        this.sku = variante.getSku();
    }
}
