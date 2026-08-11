package com.ecommerce.entity;

/**
 * Estado de una transacción de pago, espejo de los estados que reporta la pasarela.
 * No se confunde con EstadoPedido: un pago APPROVED es lo que mueve el pedido a CONFIRMADO.
 */
public enum EstadoPago {
    PENDING,      // Preferencia creada, el comprador todavía no pagó
    APPROVED,     // Pago acreditado
    REJECTED,     // Pago rechazado o cancelado por la pasarela
    IN_PROCESS,   // Pago en revisión (puede terminar aprobado o rechazado)
    REFUNDED      // Pago devuelto
}
