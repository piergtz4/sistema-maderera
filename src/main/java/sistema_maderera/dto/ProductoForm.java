package sistema_maderera.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class ProductoForm {
    private Long id;

    private String codigo;

    @NotBlank(message = "Nombre de producto: Campo obligatorio.")
    @Size(max = 150, message = "Nombre de producto: Máximo 150 caracteres.")
    private String nombre;

    @NotNull(message = "Categoría: Campo obligatorio.")
    private Long categoriaId;

    @NotBlank(message = "Unidad de medida: Campo obligatorio.")
    @Pattern(regexp = "UND|KG|P²", message = "Unidad de medida: Seleccione una unidad válida.")
    private String unidadMedida;

    @NotNull(message = "Precio de compra: Campo obligatorio.")
    @DecimalMin(value = "0", message = "Precio de compra: No puede ser negativo.")
    @Digits(integer = 8, fraction = 2, message = "Precio de compra: Máximo 8 enteros y 2 decimales.")
    private BigDecimal precioCompra = BigDecimal.ZERO;

    @NotNull(message = "Precio de venta: Campo obligatorio.")
    @DecimalMin(value = "0", message = "Precio de venta: No puede ser negativo.")
    @Digits(integer = 8, fraction = 2, message = "Precio de venta: Máximo 8 enteros y 2 decimales.")
    private BigDecimal precioVenta = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "Stock actual: No puede ser negativo.")
    @Digits(integer = 8, fraction = 4, message = "Stock actual: Máximo 8 enteros y 4 decimales.")
    private BigDecimal stockInicial;

    @DecimalMin(value = "0", message = "Stock mínimo: No puede ser negativo.")
    @Digits(integer = 8, fraction = 4, message = "Stock mínimo: Máximo 8 enteros y 4 decimales.")
    private BigDecimal stockMinimo;

    private Long inventarioVersion;

    @NotNull(message = "Estado: Campo obligatorio.")
    private Boolean estado = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public Long getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Long categoriaId) { this.categoriaId = categoriaId; }
    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }
    public BigDecimal getPrecioCompra() { return precioCompra; }
    public void setPrecioCompra(BigDecimal precioCompra) { this.precioCompra = precioCompra; }
    public BigDecimal getPrecioVenta() { return precioVenta; }
    public void setPrecioVenta(BigDecimal precioVenta) { this.precioVenta = precioVenta; }
    public BigDecimal getStockInicial() { return stockInicial; }
    public void setStockInicial(BigDecimal stockInicial) { this.stockInicial = stockInicial; }
    public BigDecimal getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(BigDecimal stockMinimo) { this.stockMinimo = stockMinimo; }
    public Long getInventarioVersion() { return inventarioVersion; }
    public void setInventarioVersion(Long inventarioVersion) { this.inventarioVersion = inventarioVersion; }
    public Boolean getEstado() { return estado; }
    public void setEstado(Boolean estado) { this.estado = estado; }
}
