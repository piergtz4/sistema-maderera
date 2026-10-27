CREATE TABLE transferencias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    movimiento_salida_id BIGINT NOT NULL UNIQUE,
    movimiento_entrada_id BIGINT NOT NULL UNIQUE,
    cantidad DECIMAL(12,4) NOT NULL,
    motivo VARCHAR(220) NOT NULL,
    fecha DATETIME(6) NOT NULL,
    CONSTRAINT fk_transferencia_salida FOREIGN KEY (movimiento_salida_id) REFERENCES movimientos_inventario(id),
    CONSTRAINT fk_transferencia_entrada FOREIGN KEY (movimiento_entrada_id) REFERENCES movimientos_inventario(id),
    CONSTRAINT ck_transferencia_cantidad CHECK (cantidad > 0)
);
