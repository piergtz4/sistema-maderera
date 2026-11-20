package sistema_maderera.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PlataformaAdministradorEmpresaForm {
    @NotBlank(message = "Nombre: Campo obligatorio.")
    @Size(max = 100, message = "Nombre: Máximo 100 caracteres.")
    private String nombre;

    @NotBlank(message = "Usuario: Campo obligatorio.")
    @Size(max = 50, message = "Usuario: Máximo 50 caracteres.")
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Usuario: Usa letras, números, puntos, guiones o guion bajo.")
    private String usuario;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
}
