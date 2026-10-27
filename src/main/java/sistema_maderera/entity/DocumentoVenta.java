package sistema_maderera.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "documentos_venta", uniqueConstraints = @UniqueConstraint(columnNames = {"sede_id", "tipo", "correlativo"}))
public class DocumentoVenta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sede_id", nullable = false)
    private Sede sede;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TipoDocumentoVenta tipo = TipoDocumentoVenta.BOLETA;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EstadoDocumentoVenta estado = EstadoDocumentoVenta.BORRADOR;

    @Column(length = 40)
    private String serie;

    @Column
    private Long correlativo;

    @Column(length = 2000)
    private String detalles;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Column(nullable = false)
    private LocalDateTime actualizado;

    @Version
    private Long version;

    @OneToMany(mappedBy = "documento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<DetalleVenta> lineas = new ArrayList<>();
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;
    @OneToMany(mappedBy = "documento", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<PagoVenta> pagos = new ArrayList<>();
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal igv = BigDecimal.ZERO;
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal tasaIgv = new BigDecimal("18.00");
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal vuelto = BigDecimal.ZERO;
    
    private LocalDateTime confirmadoEn = null;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmado_por_id")
    private Usuario confirmadoPor = null;
    @Column(length = 150)
    private String clienteNombre = null;
    @Column(length = 50)
    private String clienteDocumento = null;
    @Column(length = 255)
    private String clienteDireccion = null;
    private LocalDateTime anuladoEn;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "anulado_por_id")
    private Usuario anuladoPor;
    @Column(length = 255)
    private String motivoAnulacion;
    private Long documentoOrigenId;
    @Column(length = 65)
    private String codigoOrigen;
    public LocalDateTime getAnuladoEn() { return anuladoEn; }
    public void setAnuladoEn(LocalDateTime anuladoEn) { this.anuladoEn = anuladoEn; }
    public Usuario getAnuladoPor() { return anuladoPor; }
    public void setAnuladoPor(Usuario anuladoPor) { this.anuladoPor = anuladoPor; }
    public String getMotivoAnulacion() { return motivoAnulacion; }
    public void setMotivoAnulacion(String motivoAnulacion) { this.motivoAnulacion = motivoAnulacion; }
    public Long getDocumentoOrigenId() { return documentoOrigenId; }
    public void setDocumentoOrigenId(Long documentoOrigenId) { this.documentoOrigenId = documentoOrigenId; }
    public String getCodigoOrigen() { return codigoOrigen; }
    public void setCodigoOrigen(String codigoOrigen) { this.codigoOrigen = codigoOrigen; }
    public BigDecimal getCobroVigente() {
        return estado == EstadoDocumentoVenta.CONFIRMADO
                ? pagos.stream().map(p -> p.getAplicadoNeto()).reduce(BigDecimal.ZERO, (total, importe) -> total.add(importe)) : BigDecimal.ZERO;
    }
    public List<PagoVenta> getPagos() { return pagos; }
    public BigDecimal getSubtotal() { return subtotal; }
    public void setSubtotal(BigDecimal subtotal) { this.subtotal = subtotal; }
    public BigDecimal getIgv() { return igv; }
    public void setIgv(BigDecimal igv) { this.igv = igv; }
    public BigDecimal getTasaIgv() { return tasaIgv; }
    public void setTasaIgv(BigDecimal tasaIgv) { this.tasaIgv = tasaIgv; }
    public BigDecimal getVuelto() { return vuelto; }
    public void setVuelto(BigDecimal vuelto) { this.vuelto = vuelto; }
    public LocalDateTime getConfirmadoEn() { return confirmadoEn; }
    public void setConfirmadoEn(LocalDateTime confirmadoEn) { this.confirmadoEn = confirmadoEn; }
    public Usuario getConfirmadoPor() { return confirmadoPor; }
    public void setConfirmadoPor(Usuario confirmadoPor) { this.confirmadoPor = confirmadoPor; }
    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String clienteNombre) { this.clienteNombre = clienteNombre; }
    public String getClienteDocumento() { return clienteDocumento; }
    public void setClienteDocumento(String clienteDocumento) { this.clienteDocumento = clienteDocumento; }
    public String getClienteDireccion() { return clienteDireccion; }
    public void setClienteDireccion(String clienteDireccion) { this.clienteDireccion = clienteDireccion; }
    public String getCodigoDocumento() {
        return correlativo == null ? "Sin asignar" : serie + "-" + String.format(java.util.Locale.ROOT, "%04d", correlativo);
    }
    public BigDecimal getRecibido() { return pagos.stream().map(p -> p.getRecibido()).reduce(BigDecimal.ZERO, (total, importe) -> total.add(importe)); }
    public BigDecimal getSaldo() { return estado == EstadoDocumentoVenta.ANULADO ? BigDecimal.ZERO : total.subtract(getRecibido()).max(BigDecimal.ZERO); }

    public List<DetalleVenta> getLineas() { return lineas; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }

    public Long getId() { return id; }
    public Sede getSede() { return sede; }
    public void setSede(Sede sede) { this.sede = sede; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public Cliente getCliente() { return cliente; }
    public void setCliente(Cliente cliente) { this.cliente = cliente; }
    public TipoDocumentoVenta getTipo() { return tipo; }
    public void setTipo(TipoDocumentoVenta tipo) { this.tipo = tipo; }
    public EstadoDocumentoVenta getEstado() { return estado; }
    public void setEstado(EstadoDocumentoVenta estado) { this.estado = estado; }
    public String getSerie() { return serie; }
    public void setSerie(String serie) { this.serie = serie; }
    public Long getCorrelativo() { return correlativo; }
    public void setCorrelativo(Long correlativo) { this.correlativo = correlativo; }
    public String getDetalles() { return detalles; }
    public void setDetalles(String detalles) { this.detalles = detalles; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public LocalDateTime getActualizado() { return actualizado; }
    public void setActualizado(LocalDateTime actualizado) { this.actualizado = actualizado; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
