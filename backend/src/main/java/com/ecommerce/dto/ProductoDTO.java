package com.ecommerce.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.ecommerce.entity.Producto;
import com.ecommerce.entity.TipoProducto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoDTO {
    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private String club;
    private String liga;
    private String temporada;
    private TipoProducto tipo;
    private List<ProductoVarianteDTO> variantes;
    private Integer stockTotal;
    private List<String> images;
    private Long categoriaId;
    private String categoriaNombre;
    private Long ownerUserId;
    private String ownerUserNombre;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Constructor para mapear desde Producto
    public ProductoDTO(Producto producto) {
        this.id = producto.getId();
        this.name = producto.getName();
        this.description = producto.getDescription();
        this.price = producto.getPrice();
        this.club = producto.getClub();
        this.liga = producto.getLiga();
        this.temporada = producto.getTemporada();
        this.tipo = producto.getTipo();
        this.variantes = producto.getVariantes() == null ? List.of()
                : producto.getVariantes().stream().map(ProductoVarianteDTO::new).collect(Collectors.toList());
        this.stockTotal = this.variantes.stream().mapToInt(ProductoVarianteDTO::getStock).sum();
        this.images = producto.getImages();
        this.categoriaId = producto.getCategoria() != null ? producto.getCategoria().getId() : null;
        this.categoriaNombre = producto.getCategoria() != null ? producto.getCategoria().getNombre() : null;
        this.ownerUserId = producto.getOwnerUser() != null ? producto.getOwnerUser().getId() : null;
        this.ownerUserNombre = producto.getOwnerUser() != null ? producto.getOwnerUser().getNombre() : null;
        this.createdAt = producto.getCreatedAt();
        this.updatedAt = producto.getUpdatedAt();
    }
}
