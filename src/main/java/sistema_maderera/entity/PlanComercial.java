package sistema_maderera.entity;

import java.math.BigDecimal;

public enum PlanComercial {
    BASICO("Básico", 1, 3, 500, "39.00"),
    PROFESIONAL("Profesional", 3, 7, 2000, "79.00");

    private final String nombre;
    private final int sedes;
    private final int usuarios;
    private final int ventasMensuales;
    private final BigDecimal precioMensual;

    PlanComercial(String nombre, int sedes, int usuarios, int ventasMensuales, String precioMensual) {
        this.nombre = nombre;
        this.sedes = sedes;
        this.usuarios = usuarios;
        this.ventasMensuales = ventasMensuales;
        this.precioMensual = new BigDecimal(precioMensual);
    }

    public String getNombre() { return nombre; }
    public int getSedes() { return sedes; }
    public int getUsuarios() { return usuarios; }
    public int getVentasMensuales() { return ventasMensuales; }
    public BigDecimal getPrecioMensual() { return precioMensual; }
}
