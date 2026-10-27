CREATE TABLE cuentas_empresa (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    empresa_id BIGINT NOT NULL,
    banco VARCHAR(80) NOT NULL,
    numero VARCHAR(30) NOT NULL,
    celular_yape VARCHAR(9),
    activa BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_cuenta_empresa FOREIGN KEY (empresa_id) REFERENCES empresas(id),
    CONSTRAINT uk_cuenta_empresa UNIQUE (empresa_id, banco, numero)
);
ALTER TABLE pagos_venta ADD COLUMN cuenta_destino_id BIGINT NULL;
ALTER TABLE pagos_venta ADD CONSTRAINT fk_pago_cuenta FOREIGN KEY (cuenta_destino_id) REFERENCES cuentas_empresa(id);
