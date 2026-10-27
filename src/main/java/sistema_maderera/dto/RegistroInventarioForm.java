package sistema_maderera.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class RegistroInventarioForm {
    @NotNull(message = "Producto del catálogo: Campo obligatorio.")
    private Long productoId;

    @NotNull(message = "Stock mínimo: Campo obligatorio.")
    @DecimalMin(value = "0", message = "Stock mínimo: No puede ser negativo.")
    @Digits(integer = 8, fraction = 4, message = "Stock mínimo: Máximo 8 enteros y 4 decimales.")
    private BigDecimal stockMinimo = BigDecimal.ZERO;

    @NotNull(message = "Stock actual: Campo obligatorio.")
    @DecimalMin(value = "0", message = "Stock actual: No puede ser negativo.")
    @Digits(integer = 8, fraction = 4, message = "Stock actual: Máximo 8 enteros y 4 decimales.")
    private BigDecimal stockInicial = BigDecimal.ZERO;

    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }
    public BigDecimal getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(BigDecimal stockMinimo) { this.stockMinimo = stockMinimo; }
    public BigDecimal getStockInicial() { return stockInicial; }
    public void setStockInicial(BigDecimal stockInicial) { this.stockInicial = stockInicial; }
}
