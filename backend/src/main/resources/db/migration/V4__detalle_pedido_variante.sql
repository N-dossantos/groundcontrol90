ALTER TABLE detalle_pedidos
    ADD COLUMN producto_variante_id BIGINT NULL,
    ADD COLUMN talle VARCHAR(20) NULL;

-- Backfill: asignar la variante "UNICO" migrada en V3 a los detalles de pedido existentes
UPDATE detalle_pedidos dp
JOIN producto_variantes pv ON pv.producto_id = dp.producto_id AND pv.talle = 'UNICO'
SET dp.producto_variante_id = pv.id, dp.talle = 'UNICO';

ALTER TABLE detalle_pedidos
    MODIFY COLUMN producto_variante_id BIGINT NOT NULL,
    MODIFY COLUMN talle VARCHAR(20) NOT NULL,
    ADD CONSTRAINT fk_detalle_variante FOREIGN KEY (producto_variante_id) REFERENCES producto_variantes(id);
