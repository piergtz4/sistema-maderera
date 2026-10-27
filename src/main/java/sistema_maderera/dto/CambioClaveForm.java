package sistema_maderera.dto;

import jakarta.validation.constraints.NotBlank;

public class CambioClaveForm {
    @NotBlank(message = "Contraseña actual: Campo obligatorio.")
    private String actual;

    @NotBlank(message = "Nueva contraseña: Campo obligatorio.")
    private String nueva;

    @NotBlank(message = "Confirmación: Campo obligatorio.")
    private String confirmacion;

    public String getActual() { return actual; }
    public void setActual(String actual) { this.actual = actual; }
    public String getNueva() { return nueva; }
    public void setNueva(String nueva) { this.nueva = nueva; }
    public String getConfirmacion() { return confirmacion; }
    public void setConfirmacion(String confirmacion) { this.confirmacion = confirmacion; }
}
