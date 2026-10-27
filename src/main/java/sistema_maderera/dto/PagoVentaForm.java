package sistema_maderera.dto;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import sistema_maderera.entity.MedioPago;

public class PagoVentaForm {
    private Long cuentaDestinoId;
    public Long getCuentaDestinoId() { return cuentaDestinoId; }
    public void setCuentaDestinoId(Long cuentaDestinoId) { this.cuentaDestinoId = cuentaDestinoId; }
    @NotNull(message = "Selecciona el medio de pago.")
    private MedioPago medio = MedioPago.EFECTIVO;
    @DecimalMin(value = "0.01", message = "El monto recibido debe ser positivo.")
    @Digits(integer = 16, fraction = 2, message = "Monto recibido: máximo dos decimales y dieciséis enteros.")
    private BigDecimal recibido;
    @Size(max = 100, message = "Código de operación: máximo 100 caracteres.")
    private String referencia;
    @Size(max = 80, message = "Banco: máximo 80 caracteres.")
    private String banco;
    public MedioPago getMedio() { return medio; }
    public void setMedio(MedioPago medio) { this.medio = medio; }
    public BigDecimal getRecibido() { return recibido; }
    public void setRecibido(BigDecimal recibido) { this.recibido = recibido; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public String getBanco() { return banco; }
    public void setBanco(String banco) { this.banco = banco; }
}
