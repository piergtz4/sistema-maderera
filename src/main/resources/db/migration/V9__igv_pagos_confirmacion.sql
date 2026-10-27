ALTER TABLE documentos_venta
    ADD COLUMN subtotal DECIMAL(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN igv DECIMAL(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN tasa_igv DECIMAL(5,2) NOT NULL DEFAULT 18,
    ADD COLUMN vuelto DECIMAL(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN confirmado_en DATETIME(6),
    ADD COLUMN confirmado_por_id BIGINT,
    ADD COLUMN cliente_nombre VARCHAR(150),
    ADD COLUMN cliente_documento VARCHAR(50),
    ADD COLUMN cliente_direccion VARCHAR(255),
    ADD CONSTRAINT fk_venta_confirmador FOREIGN KEY (confirmado_por_id) REFERENCES usuarios(id);
UPDATE documentos_venta SET subtotal = ROUND(total / 1.18, 2);
UPDATE documentos_venta SET igv = total - subtotal;

CREATE TABLE pagos_venta (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    documento_id BIGINT NOT NULL,
    medio VARCHAR(20) NOT NULL,
    recibido DECIMAL(18,2) NOT NULL,
    aplicado DECIMAL(18,2) NOT NULL,
    referencia VARCHAR(100),
    banco VARCHAR(80),
    CONSTRAINT fk_pago_documento FOREIGN KEY (documento_id) REFERENCES documentos_venta(id),
    CONSTRAINT ck_pago_importes CHECK (recibido > 0 AND aplicado >= 0 AND aplicado <= recibido)
);
