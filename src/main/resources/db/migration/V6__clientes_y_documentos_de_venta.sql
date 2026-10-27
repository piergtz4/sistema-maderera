CREATE TABLE clientes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    tipo_identificacion VARCHAR(20) NOT NULL,
    numero_documento VARCHAR(20),
    nombre VARCHAR(150) NOT NULL,
    direccion VARCHAR(255),
    telefono VARCHAR(30),
    email VARCHAR(150),
    estado BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_cliente_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id),
    CONSTRAINT uq_cliente_documento UNIQUE (empresa_id, tipo_identificacion, numero_documento)
);

CREATE TABLE documentos_venta (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sede_id BIGINT NOT NULL,
    usuario_id BIGINT NOT NULL,
    cliente_id BIGINT,
    tipo VARCHAR(20) NOT NULL,
    estado VARCHAR(20) NOT NULL,
    serie VARCHAR(40),
    correlativo BIGINT,
    detalles VARCHAR(2000),
    fecha DATETIME(6) NOT NULL,
    actualizado DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_documento_sede FOREIGN KEY (sede_id) REFERENCES sedes(id),
    CONSTRAINT fk_documento_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    CONSTRAINT fk_documento_cliente FOREIGN KEY (cliente_id) REFERENCES clientes(id),
    CONSTRAINT uq_documento_numero UNIQUE (sede_id, tipo, correlativo)
);

CREATE TABLE correlativos_documento (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sede_id BIGINT NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    ultimo BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_correlativo_sede FOREIGN KEY (sede_id) REFERENCES sedes(id),
    CONSTRAINT uq_correlativo_sede_tipo UNIQUE (sede_id, tipo)
);
