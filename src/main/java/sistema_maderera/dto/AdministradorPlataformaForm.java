package sistema_maderera.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class AdministradorPlataformaForm {

    @NotBlank(message = "Ingresa el nombre.")
    @Size(max = 100, message = "El nombre admite hasta 100 caracteres.")
    private String nombre;

    @NotBlank(message = "Ingresa el usuario.")
    @Size(max = 50, message = "El usuario admite hasta 50 caracteres.")
    @Pattern(
        regexp = "[A-Za-z0-9._-]+",
        message = "Usa letras, números, puntos, guiones o guion bajo."
    )
    private String usuario;

    @NotBlank(message = "Ingresa la contraseña.")
    @Size(
        min = 12,
        max = 72,
        message = "La contraseña debe tener entre 12 y 72 caracteres."
    )
    private String password;

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}