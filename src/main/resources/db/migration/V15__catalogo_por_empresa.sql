-- El catálogo anterior debe corresponder a una sola empresa antes de asignarlo.
CREATE TEMPORARY TABLE comprobacion_catalogo_empresa (
    empresas BIGINT NOT NULL,
    registros BIGINT NOT NULL,
    CONSTRAINT ck_catalogo_empresa_unica CHECK (empresas = 1 OR registros = 0)
);

INSERT INTO comprobacion_catalogo_empresa (empresas, registros)
SELECT (SELECT COUNT(*) FROM empresas),
       (SELECT COUNT(*) FROM categorias) + (SELECT COUNT(*) FROM productos);

DROP TEMPORARY TABLE comprobacion_catalogo_empresa;

ALTER TABLE categorias ADD COLUMN empresa_id BIGINT;
UPDATE categorias
SET empresa_id = (SELECT id FROM empresas ORDER BY id LIMIT 1);

ALTER TABLE categorias
    MODIFY empresa_id BIGINT NOT NULL,
    DROP INDEX codigo,
    DROP INDEX nombre,
    ADD CONSTRAINT fk_categoria_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id),
    ADD CONSTRAINT uq_categoria_empresa_codigo UNIQUE (empresa_id, codigo),
    ADD CONSTRAINT uq_categoria_empresa_nombre UNIQUE (empresa_id, nombre),
    ADD CONSTRAINT uq_categoria_id_empresa UNIQUE (id, empresa_id);

ALTER TABLE productos ADD COLUMN empresa_id BIGINT;
UPDATE productos p
JOIN categorias c ON c.id = p.categoria_id
SET p.empresa_id = c.empresa_id;

ALTER TABLE productos
    MODIFY empresa_id BIGINT NOT NULL,
    DROP INDEX codigo,
    DROP FOREIGN KEY fk_producto_categoria,
    ADD CONSTRAINT fk_producto_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id),
    ADD CONSTRAINT uq_producto_empresa_codigo UNIQUE (empresa_id, codigo),
    ADD CONSTRAINT fk_producto_categoria_empresa FOREIGN KEY (categoria_id, empresa_id)
        REFERENCES categorias(id, empresa_id);
