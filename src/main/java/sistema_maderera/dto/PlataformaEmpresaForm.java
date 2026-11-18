package sistema_maderera.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PlataformaEmpresaForm {
    @Valid
    @NotNull(message = "Completa los datos de la empresa.")
    private EmpresaForm empresa = new EmpresaForm();

    @Valid
    @NotNull(message = "Completa los datos de la primera sede.")
    private SedeForm sede = new SedeForm();

    @NotBlank(message = "Administrador: Ingresa su nombre.")
    @Size(max = 100, message = "Administrador: Máximo 100 caracteres.")
    private String nombreAdministrador;

    @NotBlank(message = "Usuario: Campo obligatorio.")
    @Size(max = 50, message = "Usuario: Máximo 50 caracteres.")
    @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Usuario: Usa letras, números, puntos, guiones o guion bajo.")
    private String usuario;

    @NotBlank(message = "Contraseña: Campo obligatorio.")
    @Size(min = 12, max = 72, message = "Contraseña: Entre 12 y 72 caracteres.")
    private String password;

    @NotBlank(message = "Confirma la contraseña del administrador.")
    private String confirmarPassword;

    public EmpresaForm getEmpresa() { return empresa; }
    public void setEmpresa(EmpresaForm empresa) { this.empresa = empresa; }
    public SedeForm getSede() { return sede; }
    public void setSede(SedeForm sede) { this.sede = sede; }
    public String getNombreAdministrador() { return nombreAdministrador; }
    public void setNombreAdministrador(String nombreAdministrador) { this.nombreAdministrador = nombreAdministrador; }
    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmarPassword() { return confirmarPassword; }
    public void setConfirmarPassword(String confirmarPassword) { this.confirmarPassword = confirmarPassword; }
}
