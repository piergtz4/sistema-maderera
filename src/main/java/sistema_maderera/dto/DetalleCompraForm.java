package sistema_maderera.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class DetalleCompraForm {
    @NotNull(message = "Selecciona un producto.")
    private Long productoId;
    @NotNull(message = "Ingresa la cantidad.")
    @DecimalMin(value = "0.0001", message = "La cantidad debe ser mayor que cero.")
    @Digits(integer = 8, fraction = 4, message = "Cantidad: máximo ocho enteros y cuatro decimales.")
    private BigDecimal cantidad;
    @NotNull(message = "Ingresa el precio de compra.")
    @DecimalMin(value = "0", message = "El precio no puede ser negativo.")
    @Digits(integer = 8, fraction = 2, message = "Precio: máximo ocho enteros y dos decimales.")
    private BigDecimal precio;
    public Long getProductoId() { return productoId; }
    public void setProductoId(Long productoId) { this.productoId = productoId; }
    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
}
