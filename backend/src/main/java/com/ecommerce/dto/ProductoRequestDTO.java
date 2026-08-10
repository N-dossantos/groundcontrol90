package com.ecommerce.dto;

import com.ecommerce.entity.Producto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Body de POST/PUT /api/productos: el producto y su lista completa de variantes
 * (talles). La lista es la fuente de verdad del inventario del producto: lo que
 * llega acá reemplaza a las variantes existentes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoRequestDTO {
    private Producto producto;
    private List<ProductoVarianteDTO> variantes;
}
