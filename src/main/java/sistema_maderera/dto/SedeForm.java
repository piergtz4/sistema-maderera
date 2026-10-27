package sistema_maderera.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class SedeForm {
    private Long id;

    @jakarta.validation.constraints.Min(value = 1, message = "Identificador de documentos: mínimo 1.")
    @jakarta.validation.constraints.Max(value = 999, message = "Identificador de documentos: máximo 999.")
    private Integer codigoDocumentos;

    @NotBlank(message = "Nombre: Campo obligatorio.")
    @Size(max = 100, message = "Nombre: Máximo 100 caracteres.")
    private String nombre;

    @NotBlank(message = "Departamento: Campo obligatorio.")
    @Size(max = 100, message = "Departamento: Máximo 100 caracteres.")
    private String departamento;

    @NotBlank(message = "Provincia: Campo obligatorio.")
    @Size(max = 100, message = "Provincia: Máximo 100 caracteres.")
    private String provincia;

    @NotBlank(message = "Distrito: Campo obligatorio.")
    @Size(max = 100, message = "Distrito: Máximo 100 caracteres.")
    private String distrito;

    @Size(max = 255, message = "Dirección: Máximo 255 caracteres.")
    private String direccion;

    @Size(max = 20, message = "Teléfono: Máximo 20 caracteres.")
    @Pattern(regexp = "^$|[0-9+() .-]+", message = "Teléfono: Formato no válido.")
    private String telefono;

    public Long getId() { return id; }
    public Integer getCodigoDocumentos() { return codigoDocumentos; }
    public void setCodigoDocumentos(Integer codigoDocumentos) { this.codigoDocumentos = codigoDocumentos; }
    public void setId(Long id) { this.id = id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDepartamento() { return departamento; }
    public void setDepartamento(String departamento) { this.departamento = departamento; }
    public String getProvincia() { return provincia; }
    public void setProvincia(String provincia) { this.provincia = provincia; }
    public String getDistrito() { return distrito; }
    public void setDistrito(String distrito) { this.distrito = distrito; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
}
