package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "productos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;
    
    @Column(nullable = false)
    private String club;

    @Column(nullable = false)
    private String liga;

    @Column(nullable = false)
    private String temporada;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoProducto tipo;

    // El stock vive únicamente en las variantes (un stock por talle)
    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ProductoVariante> variantes = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "producto_imagenes", joinColumns = @JoinColumn(name = "producto_id"))
    @Column(name = "imagen_url")
    private List<String> images;
    
    // Relación con categoría (muchos productos pertenecen a una categoría)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Categoria categoria;
    
    // Relación con usuario propietario (muchos productos pertenecen a un usuario)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private Usuario ownerUser;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    @Override
    public String toString() {
        return "Producto{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", price=" + price +
                ", club='" + club + '\'' +
                ", liga='" + liga + '\'' +
                ", temporada='" + temporada + '\'' +
                ", tipo=" + tipo +
                ", variantes=" + (variantes != null ? variantes.size() : 0) +
                ", categoria=" + (categoria != null ? categoria.getId() : null) +
                ", ownerUser=" + (ownerUser != null ? ownerUser.getId() : null) +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}
