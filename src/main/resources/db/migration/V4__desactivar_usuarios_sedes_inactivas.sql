UPDATE usuarios
SET estado = FALSE
WHERE estado = TRUE AND sede_id IN (SELECT id FROM sedes WHERE estado = FALSE);
