CREATE TABLE producto_variantes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    producto_id BIGINT NOT NULL,
    talle VARCHAR(20) NOT NULL,
    stock INT NOT NULL,
    sku VARCHAR(100) NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_variante_producto FOREIGN KEY (producto_id) REFERENCES productos(id)
);

-- Migrar el stock agregado existente a una variante única "UNICO" antes de borrar la columna,
-- para no perder inventario cargado en producción.
INSERT INTO producto_variantes (producto_id, talle, stock, sku, version)
SELECT id, 'UNICO', stock, CONCAT('LEGACY-', id), 0
FROM productos;

ALTER TABLE productos DROP COLUMN stock;
