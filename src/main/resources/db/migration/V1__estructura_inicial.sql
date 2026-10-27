CREATE TABLE sedes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    departamento VARCHAR(100) NOT NULL,
    provincia VARCHAR(100) NOT NULL,
    distrito VARCHAR(100) NOT NULL,
    direccion VARCHAR(255),
    telefono VARCHAR(20),
    estado BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE usuarios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    usuario VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    rol VARCHAR(30) NOT NULL,
    sede_id BIGINT NOT NULL,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_usuario_sede FOREIGN KEY (sede_id) REFERENCES sedes(id)
);

CREATE TABLE categorias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(20) NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL UNIQUE,
    descripcion VARCHAR(255),
    estado BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE productos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    codigo VARCHAR(30) NOT NULL UNIQUE,
    nombre VARCHAR(150) NOT NULL,
    categoria_id BIGINT NOT NULL,
    unidad_medida VARCHAR(10) NOT NULL,
    precio_compra DECIMAL(10,2) NOT NULL DEFAULT 0,
    precio_venta DECIMAL(10,2) NOT NULL DEFAULT 0,
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_producto_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id),
    CONSTRAINT ck_precios CHECK (precio_compra >= 0 AND precio_venta >= 0)
);

CREATE TABLE inventarios (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    producto_id BIGINT NOT NULL,
    sede_id BIGINT NOT NULL,
    stock DECIMAL(12,4) NOT NULL DEFAULT 0,
    stock_minimo DECIMAL(12,4) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_inventario_producto_sede UNIQUE (producto_id, sede_id),
    CONSTRAINT fk_inventario_producto FOREIGN KEY (producto_id) REFERENCES productos(id),
    CONSTRAINT fk_inventario_sede FOREIGN KEY (sede_id) REFERENCES sedes(id),
    CONSTRAINT ck_stock CHECK (stock >= 0 AND stock_minimo >= 0),
    INDEX ix_inventario_sede (sede_id)
);

CREATE TABLE movimientos_inventario (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    inventario_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    cantidad DECIMAL(12,4) NOT NULL,
    stock_anterior DECIMAL(12,4) NOT NULL,
    stock_posterior DECIMAL(12,4) NOT NULL,
    motivo VARCHAR(255) NOT NULL,
    fecha DATETIME(6) NOT NULL,
    CONSTRAINT fk_movimiento_inventario FOREIGN KEY (inventario_id) REFERENCES inventarios(id),
    CONSTRAINT fk_movimiento_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT ck_movimiento_stock CHECK (stock_anterior >= 0 AND stock_posterior >= 0),
    INDEX ix_movimiento_fecha (inventario_id, fecha, id)
);
