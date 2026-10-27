package sistema_maderera.entity;

public enum TipoDocumentoVenta {
    BOLETA("Boleta", "B"), FACTURA("Factura", "F"), NOTA_VENTA("Nota de venta", "N"), COTIZACION("Cotización", "C");

    private final String nombre;
    private final String prefijo;
    TipoDocumentoVenta(String nombre, String prefijo) { this.nombre = nombre; this.prefijo = prefijo; }
    public String getNombre() { return nombre; }
    public String serie(Integer codigoSede) { return prefijo + String.format(java.util.Locale.ROOT, "%03d", codigoSede); }
}
