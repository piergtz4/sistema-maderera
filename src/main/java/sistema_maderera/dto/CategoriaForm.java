package sistema_maderera.dto;

import jakarta.validation.constraints.*;

public class CategoriaForm {
    private Long id;

    private String codigo;

    @NotBlank(message = "Nombre de categoría: Campo obligatorio.")
    @Size(max = 100, message = "Nombre de categoría: Máximo 100 caracteres.")
    private String nombre;

    @Size(max = 255, message = "Descripción: Máximo 255 caracteres.")
    private String descripcion;

    @NotNull(message = "Estado: Campo obligatorio.")
    private Boolean estado = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Boolean getEstado() { return estado; }
    public void setEstado(Boolean estado) { this.estado = estado; }
}
