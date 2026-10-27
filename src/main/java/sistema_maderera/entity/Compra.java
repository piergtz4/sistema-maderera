package sistema_maderera.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "compras", uniqueConstraints = @UniqueConstraint(columnNames = {"proveedor_id", "tipo", "referencia"}))
public class Compra {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Version
    private Long version;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "sede_id", nullable = false)
    private Sede sede;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private EstadoCompra estado = EstadoCompra.PENDIENTE;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR) @Column(nullable = false, length = 20)
    private TipoComprobanteCompra tipo;
    @Column(nullable = false, length = 60)
    private String referencia;
    @Column(nullable = false)
    private LocalDate fecha;
    @Column(nullable = false)
    private LocalDateTime actualizado;
    private LocalDateTime confirmadoEn;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "confirmado_por_id")
    private Usuario confirmadoPor;
    @Column(nullable = false, length = 150)
    private String proveedorNombre;
    @Column(length = 50)
    private String proveedorDocumento;
    @Column(length = 2000)
    private String detalles;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;
    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, orphanRemoval = true) @OrderBy("id ASC")
    private List<DetalleCompra> lineas = new ArrayList<>();
    public Long getId() { return id; }
    public Long getVersion() { return version; }
    public Sede getSede() { return sede; }
    public void setSede(Sede sede) { this.sede = sede; }
    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public EstadoCompra getEstado() { return estado; }
    public void setEstado(EstadoCompra estado) { this.estado = estado; }
    public TipoComprobanteCompra getTipo() { return tipo; }
    public void setTipo(TipoComprobanteCompra tipo) { this.tipo = tipo; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public LocalDateTime getActualizado() { return actualizado; }
    public void setActualizado(LocalDateTime actualizado) { this.actualizado = actualizado; }
    public LocalDateTime getConfirmadoEn() { return confirmadoEn; }
    public void setConfirmadoEn(LocalDateTime confirmadoEn) { this.confirmadoEn = confirmadoEn; }
    public Usuario getConfirmadoPor() { return confirmadoPor; }
    public void setConfirmadoPor(Usuario confirmadoPor) { this.confirmadoPor = confirmadoPor; }
    public String getProveedorNombre() { return proveedorNombre; }
    public void setProveedorNombre(String nombre) { proveedorNombre = nombre; }
    public String getProveedorDocumento() { return proveedorDocumento; }
    public void setProveedorDocumento(String documento) { proveedorDocumento = documento; }
    public String getDetalles() { return detalles; }
    public void setDetalles(String detalles) { this.detalles = detalles; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal total) { this.total = total; }
    public List<DetalleCompra> getLineas() { return lineas; }
    public String getCodigo() { return "COMP-" + String.format(java.util.Locale.ROOT, "%06d", id); }
}
