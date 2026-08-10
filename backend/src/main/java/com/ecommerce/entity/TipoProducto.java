package com.ecommerce.entity;

/**
 * Tipo de prenda del catálogo. Conjunto cerrado, por eso es un enum Java
 * (a diferencia del talle, que es texto libre para no requerir una migración
 * de esquema cada vez que se agrega un talle nuevo).
 */
public enum TipoProducto {
    CAMISETA,
    SHORT
}
