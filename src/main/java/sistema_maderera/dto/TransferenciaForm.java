package sistema_maderera.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class TransferenciaForm {
    @NotNull(message = "Producto de origen: Campo obligatorio.")
    private Long inventarioOrigenId;

    @NotNull(message = "Sede de destino: Campo obligatorio.")
    private Long sedeDestinoId;

    @NotNull(message = "Cantidad: Campo obligatorio.")
    @DecimalMin(value = "0.0001", message = "Cantidad: Debe ser mayor que cero.")
    @Digits(integer = 8, fraction = 4, message = "Cantidad: Máximo 8 enteros y 4 decimales.")
    private BigDecimal cantidad;

    @NotBlank(message = "Motivo: Campo obligatorio.")
    @Size(max = 220, message = "Motivo: Máximo 220 caracteres.")
    private String motivo;

    public Long getInventarioOrigenId() { return inventarioOrigenId; }
    public void setInventarioOrigenId(Long inventarioOrigenId) { this.inventarioOrigenId = inventarioOrigenId; }
    public Long getSedeDestinoId() { return sedeDestinoId; }
    public void setSedeDestinoId(Long sedeDestinoId) { this.sedeDestinoId = sedeDestinoId; }
    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
}
