package com.ecommerce.dto;

import com.ecommerce.entity.TipoProducto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Filtros opcionales del catálogo. Un campo en null significa "no filtrar por eso".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoFiltroDTO {
    private String club;
    private String liga;
    private TipoProducto tipo;
    private String talle;
    private BigDecimal precioMin;
    private BigDecimal precioMax;
}
