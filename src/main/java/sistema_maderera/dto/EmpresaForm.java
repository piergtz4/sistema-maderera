package sistema_maderera.dto;

import jakarta.validation.constraints.*;

public class EmpresaForm {
    @NotBlank(message = "Nombre comercial: Campo obligatorio.")
    @Size(max = 100, message = "Nombre comercial: Máximo 100 caracteres.")
    private String nombreComercial;

    @Size(max = 150, message = "Razón social: Máximo 150 caracteres.")
    private String razonSocial;

    @Pattern(regexp = "^$|\\d{11}", message = "RUC: Ingresa exactamente 11 dígitos.")
    private String ruc;

    @Size(max = 30, message = "Teléfono: Máximo 30 caracteres.")
    @Pattern(regexp = "^$|[0-9+() .-]+", message = "Teléfono: Formato no válido.")
    private String telefono;

    @Size(max = 150, message = "Correo: Máximo 150 caracteres.")
    @Email(message = "Correo: Formato no válido.")
    private String email;

    @Size(max = 255, message = "Dirección: Máximo 255 caracteres.")
    private String direccion;

    private boolean eliminarLogo;

    public String getNombreComercial() { return nombreComercial; }
    public void setNombreComercial(String nombreComercial) { this.nombreComercial = nombreComercial; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public String getRuc() { return ruc; }
    public void setRuc(String ruc) { this.ruc = ruc; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public boolean isEliminarLogo() { return eliminarLogo; }
    public void setEliminarLogo(boolean eliminarLogo) { this.eliminarLogo = eliminarLogo; }
}
