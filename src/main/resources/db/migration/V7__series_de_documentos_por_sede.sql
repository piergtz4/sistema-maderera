ALTER TABLE sedes ADD COLUMN codigo_documentos INT;

UPDATE sedes s
JOIN (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY empresa_id ORDER BY id) AS codigo
    FROM sedes
) numeracion ON numeracion.id = s.id
SET s.codigo_documentos = numeracion.codigo;

ALTER TABLE sedes MODIFY COLUMN codigo_documentos INT NOT NULL;
ALTER TABLE sedes ADD CONSTRAINT uq_sede_codigo_documentos UNIQUE (empresa_id, codigo_documentos);
ALTER TABLE sedes ADD CONSTRAINT ck_sede_codigo_documentos CHECK (codigo_documentos BETWEEN 1 AND 999);
