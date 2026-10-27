package sistema_maderera.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class StockMinimoForm {
    @NotNull(message = "Stock mínimo: Campo obligatorio.")
    @DecimalMin(value = "0", message = "Stock mínimo: No puede ser negativo.")
    @Digits(integer = 8, fraction = 4, message = "Stock mínimo: Máximo 8 enteros y 4 decimales.")
    private BigDecimal stockMinimo;

    public BigDecimal getStockMinimo() { return stockMinimo; }
    public void setStockMinimo(BigDecimal stockMinimo) { this.stockMinimo = stockMinimo; }
}
