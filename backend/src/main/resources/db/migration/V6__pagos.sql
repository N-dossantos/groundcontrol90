CREATE TABLE pagos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    proveedor VARCHAR(50) NOT NULL,
    preference_id VARCHAR(255),
    payment_id_externo VARCHAR(255),
    estado ENUM('PENDING','APPROVED','REJECTED','IN_PROCESS','REFUNDED') NOT NULL,
    monto DECIMAL(10,2) NOT NULL,
    moneda VARCHAR(10) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    CONSTRAINT fk_pago_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos(id)
);

CREATE INDEX idx_pagos_pedido_id ON pagos(pedido_id);
CREATE INDEX idx_pagos_preference_id ON pagos(preference_id);

-- PAGO_RECHAZADO es el estado terminal de un pedido cuya pasarela rechazó el pago.
-- estado/estado_item son ENUM de MySQL (ver V1 y el comentario de V2): sin este ALTER
-- MySQL rechazaría el UPDATE con "Data truncated" y Hibernate fallaría al validar el esquema.
ALTER TABLE pedidos
    MODIFY estado ENUM('PENDIENTE','CONFIRMADO','PREPARANDO','ENVIADO','EN_TRANSITO','ENTREGADO','CANCELADO','CANCELADO_COMPRADOR','CANCELADO_VENDEDOR','DEVOLUCION_SOLICITADA','DEVUELTO','PAGO_RECHAZADO') NOT NULL;

ALTER TABLE detalle_pedidos
    MODIFY estado_item ENUM('PENDIENTE','CONFIRMADO','PREPARANDO','ENVIADO','EN_TRANSITO','ENTREGADO','CANCELADO','CANCELADO_COMPRADOR','CANCELADO_VENDEDOR','DEVOLUCION_SOLICITADA','DEVUELTO','PAGO_RECHAZADO') NOT NULL;
