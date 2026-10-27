ALTER TABLE pagos_venta DROP CHECK ck_pago_importes;
ALTER TABLE pagos_venta ADD CONSTRAINT ck_pago_importes CHECK (recibido >= 0 AND aplicado >= 0 AND aplicado <= recibido);
