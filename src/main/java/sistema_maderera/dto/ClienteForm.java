package sistema_maderera.dto;

import jakarta.validation.constraints.*;
import sistema_maderera.entity.TipoIdentificacion;

public class ClienteForm {
    private Long id;
    private Long version;
    @NotNull(message = "Selecciona el tipo de documento.")
    private TipoIdentificacion tipoIdentificacion = TipoIdentificacion.SIN_DOCUMENTO;
    @Size(max = 20, message = "Documento: máximo 20 caracteres.")
    private String numeroDocumento;
    @NotBlank(message = "Ingresa el nombre o razón social.")
    @Size(max = 150, message = "Nombre: máximo 150 caracteres.")
    private String nombre;
    @Size(max = 255, message = "Dirección: máximo 255 caracteres.")
    private String direccion;
    @Size(max = 30, message = "Teléfono: máximo 30 caracteres.")
    private String telefono;
    @Email(message = "Ingresa un correo válido.")
    @Size(max = 150, message = "Correo: máximo 150 caracteres.")
    private String email;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public TipoIdentificacion getTipoIdentificacion() { return tipoIdentificacion; }
    public void setTipoIdentificacion(TipoIdentificacion tipoIdentificacion) { this.tipoIdentificacion = tipoIdentificacion; }
    public String getNumeroDocumento() { return numeroDocumento; }
    public void setNumeroDocumento(String numeroDocumento) { this.numeroDocumento = numeroDocumento; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}

