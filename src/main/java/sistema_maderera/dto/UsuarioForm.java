package sistema_maderera.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class UsuarioForm {
    private Long id;

    @NotBlank(message = "Nombre: Campo obligatorio.")
    @Size(max = 100, message = "Nombre: Máximo 100 caracteres.")
    private String nombre;

    @NotBlank(message = "Usuario: Campo obligatorio.")
    @Size(max = 50, message = "Usuario: Máximo 50 caracteres.")
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Usuario: Usa letras, números, puntos, guiones o guion bajo.")
    private String usuario;

    @NotBlank(message = "Rol: Campo obligatorio.")
    private String rol;
    private Long sedeId;

    @Size(max = 72, message = "Contraseña: Máximo 72 caracteres.")
    private String password;
    private String confirmarPassword;
    private String claveAdministrador;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    public Long getSedeId() { return sedeId; }
    public void setSedeId(Long sedeId) { this.sedeId = sedeId; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmarPassword() { return confirmarPassword; }
    public void setConfirmarPassword(String confirmarPassword) { this.confirmarPassword = confirmarPassword; }
    public String getClaveAdministrador() { return claveAdministrador; }
    public void setClaveAdministrador(String claveAdministrador) { this.claveAdministrador = claveAdministrador; }
}
