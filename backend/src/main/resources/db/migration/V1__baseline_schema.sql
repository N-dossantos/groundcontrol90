CREATE TABLE usuarios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL,
    apellido VARCHAR(255) NOT NULL,
    username VARCHAR(255) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role ENUM('USER','ADMIN') NOT NULL,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE categorias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(255) NOT NULL UNIQUE,
    descripcion TEXT,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE productos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL,
    category_id BIGINT,
    owner_user_id BIGINT,
    created_at DATETIME,
    updated_at DATETIME,
    CONSTRAINT fk_productos_categoria FOREIGN KEY (category_id) REFERENCES categorias(id),
    CONSTRAINT fk_productos_owner FOREIGN KEY (owner_user_id) REFERENCES usuarios(id)
);

CREATE TABLE producto_imagenes (
    producto_id BIGINT NOT NULL,
    imagen_url VARCHAR(255),
    CONSTRAINT fk_producto_imagenes_producto FOREIGN KEY (producto_id) REFERENCES productos(id)
);

CREATE TABLE pedidos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    total DECIMAL(10,2) NOT NULL,
    estado ENUM('PENDIENTE','CONFIRMADO','PREPARANDO','ENVIADO','EN_TRANSITO','ENTREGADO','CANCELADO','CANCELADO_COMPRADOR','CANCELADO_VENDEDOR','DEVOLUCION_SOLICITADA','DEVUELTO') NOT NULL,
    direccion_envio TEXT,
    notas VARCHAR(255),
    created_at DATETIME NOT NULL,
    updated_at DATETIME,
    CONSTRAINT fk_pedidos_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
);

CREATE TABLE detalle_pedidos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    vendedor_id BIGINT NOT NULL,
    cantidad INT NOT NULL,
    precio_unitario DECIMAL(10,2) NOT NULL,
    producto_nombre VARCHAR(255) NOT NULL,
    producto_imagen VARCHAR(255),
    estado_item ENUM('PENDIENTE','CONFIRMADO','PREPARANDO','ENVIADO','EN_TRANSITO','ENTREGADO','CANCELADO','CANCELADO_COMPRADOR','CANCELADO_VENDEDOR','DEVOLUCION_SOLICITADA','DEVUELTO') NOT NULL,
    CONSTRAINT fk_detalle_pedido FOREIGN KEY (pedido_id) REFERENCES pedidos(id),
    CONSTRAINT fk_detalle_producto FOREIGN KEY (producto_id) REFERENCES productos(id),
    CONSTRAINT fk_detalle_vendedor FOREIGN KEY (vendedor_id) REFERENCES usuarios(id)
);
