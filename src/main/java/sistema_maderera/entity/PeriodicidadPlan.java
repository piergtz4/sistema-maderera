package sistema_maderera.entity;

public enum PeriodicidadPlan {
    MENSUAL("Mensual", 1), ANUAL("Anual", 12);

    private final String nombre;
    private final int meses;

    PeriodicidadPlan(String nombre, int meses) { this.nombre = nombre; this.meses = meses; }
    public String getNombre() { return nombre; }
    public int getMeses() { return meses; }
}
