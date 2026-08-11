package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReporteVentasDTO {
    private BigDecimal ventasTotales;
    private BigDecimal ticketPromedio;
    private Integer cantidadPedidos;
    private List<ProductoMasVendidoDTO> productosMasVendidos;
}
