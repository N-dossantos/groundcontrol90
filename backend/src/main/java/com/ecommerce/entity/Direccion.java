package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Dirección de envío guardada por un usuario (address book).
 * Cada usuario solo ve y edita las suyas.
 */
@Entity
@Table(name = "direcciones")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Direccion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    @ToString.Exclude
    private Usuario usuario;

    @Column(nullable = false)
    private String calle;

    @Column(nullable = false)
    private String numero;

    @Column(nullable = false)
    private String ciudad;

    @Column(nullable = false)
    private String provincia;

    @Column(name = "codigo_postal", nullable = false)
    private String codigoPostal;

    @Column(nullable = false)
    private String pais;

    @Column(name = "es_predeterminada", nullable = false)
    @Builder.Default
    private boolean esPredeterminada = false;
}
