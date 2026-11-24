CREATE TABLE usuario_sedes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    sede_id BIGINT NOT NULL,
    puede_gestionar BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_usuario_sede UNIQUE (usuario_id, sede_id),
    CONSTRAINT fk_acceso_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT fk_acceso_sede FOREIGN KEY (sede_id) REFERENCES sedes(id) ON DELETE CASCADE
);

INSERT INTO usuario_sedes (usuario_id, sede_id, puede_gestionar)
SELECT u.id, u.sede_id, u.rol = 'ADMINISTRADOR'
FROM usuarios u JOIN sedes s ON s.id = u.sede_id AND s.empresa_id = u.empresa_id
WHERE u.rol <> 'ADMINISTRADOR_GENERAL';

UPDATE usuarios SET rol = 'CONTADOR', version_acceso = version_acceso + 1 WHERE rol = 'CONSULTA';
