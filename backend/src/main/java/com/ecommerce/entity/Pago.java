package com.ecommerce.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transacción de pago asociada a un pedido.
 * Guarda los identificadores externos de la pasarela (preferencia y pago) para poder
 * correlacionar un webhook entrante con el pedido que le corresponde.
 */
@Entity
@Table(name = "pagos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Excluido de toString: Pedido ya imprime su propio id y el lazy loading acá
    // dispararía una query extra en cada log.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    @ToString.Exclude
    private Pedido pedido;

    @Column(nullable = false)
    @Builder.Default
    private String proveedor = "MERCADOPAGO";

    @Column(name = "preference_id")
    private String preferenceId;

    @Column(name = "payment_id_externo")
    private String paymentIdExterno;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EstadoPago estado = EstadoPago.PENDING;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    @Builder.Default
    private String moneda = "ARS";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
