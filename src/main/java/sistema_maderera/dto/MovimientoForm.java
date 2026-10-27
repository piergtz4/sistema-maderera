package sistema_maderera.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import sistema_maderera.entity.TipoMovimiento;

public class MovimientoForm {
    @NotNull(message = "Producto de esta sede: Campo obligatorio.")
    private Long inventarioId;

    @NotNull(message = "Tipo de movimiento: Campo obligatorio.")
    private TipoMovimiento tipo;

    @NotNull(message = "Cantidad: Campo obligatorio.")
    @DecimalMin(value = "0", message = "Cantidad: No puede ser negativo.")
    @Digits(integer = 8, fraction = 4, message = "Cantidad: Máximo 8 enteros y 4 decimales.")
    private BigDecimal cantidad;

    @NotBlank(message = "Motivo: Campo obligatorio.")
    @Size(max = 255, message = "Motivo: Máximo 255 caracteres.")
    private String motivo;

    public Long getInventarioId() { return inventarioId; }
    public void setInventarioId(Long inventarioId) { this.inventarioId = inventarioId; }
    public TipoMovimiento getTipo() { return tipo; }
    public void setTipo(TipoMovimiento tipo) { this.tipo = tipo; }
    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
