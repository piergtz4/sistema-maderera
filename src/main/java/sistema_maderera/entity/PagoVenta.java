package sistema_maderera.entity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "pagos_venta")
public class PagoVenta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private DocumentoVenta documento;
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private MedioPago medio;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal recibido;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal aplicado;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal revertido = BigDecimal.ZERO;
    public BigDecimal getRevertido() { return revertido; }
    public void setRevertido(BigDecimal revertido) { this.revertido = revertido; }
    public BigDecimal getAplicadoNeto() { return aplicado.subtract(revertido); }
    @Column(length = 100)
    private String referencia;
    @Column(length = 80)
    private String banco;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cuenta_destino_id")
    private CuentaEmpresa cuentaDestino;
    public CuentaEmpresa getCuentaDestino() { return cuentaDestino; }
    public void setCuentaDestino(CuentaEmpresa cuentaDestino) { this.cuentaDestino = cuentaDestino; }
    public void setDocumento(DocumentoVenta documento) { this.documento = documento; }
    public MedioPago getMedio() { return medio; }
    public void setMedio(MedioPago medio) { this.medio = medio; }
    public BigDecimal getRecibido() { return recibido; }
    public void setRecibido(BigDecimal recibido) { this.recibido = recibido; }
    public BigDecimal getAplicado() { return aplicado; }
    public void setAplicado(BigDecimal aplicado) { this.aplicado = aplicado; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public String getBanco() { return banco; }
    public void setBanco(String banco) { this.banco = banco; }
}
