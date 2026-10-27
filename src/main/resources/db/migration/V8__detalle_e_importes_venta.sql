ALTER TABLE documentos_venta ADD COLUMN total DECIMAL(18,2) NOT NULL DEFAULT 0;

CREATE TABLE detalles_venta (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    documento_id BIGINT NOT NULL,
    producto_id BIGINT NOT NULL,
    codigo VARCHAR(30) NOT NULL,
    nombre VARCHAR(150) NOT NULL,
    unidad_medida VARCHAR(10) NOT NULL,
    cantidad DECIMAL(14,4) NOT NULL,
    precio DECIMAL(10,2) NOT NULL,
    importe_calculado DECIMAL(16,2) NOT NULL,
    importe_cobrar DECIMAL(16,2) NOT NULL,
    CONSTRAINT fk_detalle_documento FOREIGN KEY (documento_id) REFERENCES documentos_venta(id),
    CONSTRAINT fk_detalle_producto FOREIGN KEY (producto_id) REFERENCES productos(id),
    CONSTRAINT ck_detalle_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_detalle_importes CHECK (precio >= 0 AND importe_calculado >= 0 AND importe_cobrar >= 0)
);
