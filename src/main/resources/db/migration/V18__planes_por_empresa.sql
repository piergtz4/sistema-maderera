ALTER TABLE empresas
    ADD COLUMN plan_comercial VARCHAR(20) NULL,
    ADD COLUMN periodicidad_plan VARCHAR(10) NOT NULL DEFAULT 'MENSUAL',
    ADD COLUMN sedes_adicionales INT NOT NULL DEFAULT 0,
    ADD COLUMN usuarios_adicionales INT NOT NULL DEFAULT 0,
    ADD COLUMN version_plan BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_empresa_plan CHECK (plan_comercial IS NULL OR plan_comercial IN ('BASICO', 'PROFESIONAL')),
    ADD CONSTRAINT chk_empresa_periodicidad CHECK (periodicidad_plan IN ('MENSUAL', 'ANUAL')),
    ADD CONSTRAINT chk_empresa_adicionales CHECK (sedes_adicionales >= 0 AND usuarios_adicionales >= 0);
