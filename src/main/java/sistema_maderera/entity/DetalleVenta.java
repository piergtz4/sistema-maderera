package sistema_maderera.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "detalles_venta")
public class DetalleVenta {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private DocumentoVenta documento;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;
    @Column(nullable = false, length = 30)
    private String codigo;
    @Column(nullable = false, length = 150)
    private String nombre;
    @Column(nullable = false, length = 10)
    private String unidadMedida;
    @Column(nullable = false, precision = 14, scale = 4)
    private BigDecimal cantidad;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal precio;
    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal importeCalculado;
    @Column(nullable = false, precision = 16, scale = 2)
    private BigDecimal importeCobrar;
    public void setDocumento(DocumentoVenta documento) { this.documento = documento; }
    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }
    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
    public BigDecimal getPrecio() { return precio; }
    public void setPrecio(BigDecimal precio) { this.precio = precio; }
    public BigDecimal getImporteCalculado() { return importeCalculado; }
    public void setImporteCalculado(BigDecimal importeCalculado) { this.importeCalculado = importeCalculado; }
    public BigDecimal getImporteCobrar() { return importeCobrar; }
    public void setImporteCobrar(BigDecimal importeCobrar) { this.importeCobrar = importeCobrar; }
    public BigDecimal getAjuste() { return importeCobrar.subtract(importeCalculado); }
}
