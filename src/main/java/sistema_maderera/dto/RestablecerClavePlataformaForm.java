package sistema_maderera.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RestablecerClavePlataformaForm {
    @NotBlank(message = "Ingresa la nueva contraseña.")
    @Size(min = 12, max = 72, message = "La nueva contraseña debe tener entre 12 y 72 caracteres.")
    private String nueva;

    @NotBlank(message = "Confirma la nueva contraseña.")
    private String confirmacion;

    @NotBlank(message = "Ingresa tu contraseña de administrador de plataforma.")
    @Size(max = 72, message = "La contraseña de tu cuenta es demasiado larga.")
    private String clavePlataforma;

    public String getNueva() { return nueva; }
    public void setNueva(String nueva) { this.nueva = nueva; }
    public String getConfirmacion() { return confirmacion; }
    public void setConfirmacion(String confirmacion) { this.confirmacion = confirmacion; }
    public String getClavePlataforma() { return clavePlataforma; }
    public void setClavePlataforma(String clavePlataforma) { this.clavePlataforma = clavePlataforma; }
}
