ALTER TABLE documentos_venta
    ADD COLUMN anulado_en DATETIME(6),
    ADD COLUMN anulado_por_id BIGINT,
    ADD COLUMN motivo_anulacion VARCHAR(255),
    ADD COLUMN documento_origen_id BIGINT,
    ADD COLUMN codigo_origen VARCHAR(65),
    ADD CONSTRAINT fk_venta_anulador FOREIGN KEY (anulado_por_id) REFERENCES usuarios(id),
    ADD CONSTRAINT fk_venta_origen FOREIGN KEY (documento_origen_id) REFERENCES documentos_venta(id) ON DELETE SET NULL;
CREATE INDEX idx_venta_origen_estado ON documentos_venta (documento_origen_id, estado);
ALTER TABLE pagos_venta ADD COLUMN revertido DECIMAL(18,2) NOT NULL DEFAULT 0;
ALTER TABLE pagos_venta ADD CONSTRAINT ck_pago_reversion CHECK (revertido >= 0 AND revertido <= aplicado);
