package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Variante comprable de un producto: un talle concreto con su propio stock y SKU.
 * Es la única fuente de verdad del inventario (Producto ya no tiene stock agregado).
 */
@Entity
@Table(name = "producto_variantes")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductoVariante {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Excluido de toString/equals/hashCode: Producto tiene la colección inversa
    // y el ciclo produciría recursión infinita en los métodos generados por Lombok.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Producto producto;

    @Column(nullable = false)
    private String talle;

    @Column(nullable = false)
    private Integer stock;

    @Column(nullable = false, unique = true)
    private String sku;

    @Version
    private Long version;
}
