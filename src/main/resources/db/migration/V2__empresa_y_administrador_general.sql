CREATE TABLE empresas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre_comercial VARCHAR(100) NOT NULL,
    razon_social VARCHAR(150),
    ruc VARCHAR(11) UNIQUE,
    telefono VARCHAR(30),
    email VARCHAR(150),
    direccion VARCHAR(255),
    logo_datos LONGBLOB,
    logo_tipo VARCHAR(30),
    estado BOOLEAN NOT NULL DEFAULT TRUE
);

INSERT INTO empresas (nombre_comercial, estado)
VALUES ('Mi Empresa Maderera', TRUE);

ALTER TABLE sedes ADD COLUMN empresa_id BIGINT;
UPDATE sedes SET empresa_id = (SELECT id FROM empresas ORDER BY id LIMIT 1);
ALTER TABLE sedes MODIFY empresa_id BIGINT NOT NULL;
ALTER TABLE sedes ADD CONSTRAINT fk_sede_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id);
CREATE INDEX ix_sede_empresa ON sedes (empresa_id);

ALTER TABLE usuarios ADD COLUMN empresa_id BIGINT;
UPDATE usuarios u
JOIN sedes s ON s.id = u.sede_id
SET u.empresa_id = s.empresa_id;
ALTER TABLE usuarios MODIFY empresa_id BIGINT NOT NULL;
ALTER TABLE usuarios MODIFY sede_id BIGINT NULL;
ALTER TABLE usuarios ADD CONSTRAINT fk_usuario_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id);
CREATE INDEX ix_usuario_empresa ON usuarios (empresa_id);

UPDATE usuarios
SET rol = 'ADMINISTRADOR_GENERAL', sede_id = NULL, nombre = 'Administrador general'
WHERE usuario = 'admin';
