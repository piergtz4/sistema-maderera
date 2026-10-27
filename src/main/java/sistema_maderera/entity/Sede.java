package sistema_maderera.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "sedes", uniqueConstraints = @UniqueConstraint(columnNames = {"empresa_id", "codigo_documentos"}))
public class Sede {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;

    @Column(name = "codigo_documentos", nullable = false)
    private Integer codigoDocumentos;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String departamento;

    @Column(nullable = false, length = 100)
    private String provincia;

    @Column(nullable = false, length = 100)
    private String distrito;

    @Column(length = 255)
    private String direccion;

    @Column(length = 20)
    private String telefono;

    @Column(nullable = false)
    private Boolean estado = true;

    public Long getId() { return id; }
    public Integer getCodigoDocumentos() { return codigoDocumentos; }
    public void setCodigoDocumentos(Integer codigoDocumentos) { this.codigoDocumentos = codigoDocumentos; }
    public String getSerieBoleta() { return TipoDocumentoVenta.BOLETA.serie(codigoDocumentos); }
    public String getSerieFactura() { return TipoDocumentoVenta.FACTURA.serie(codigoDocumentos); }
    public String getSerieNotaVenta() { return TipoDocumentoVenta.NOTA_VENTA.serie(codigoDocumentos); }
    public String getSerieCotizacion() { return TipoDocumentoVenta.COTIZACION.serie(codigoDocumentos); }
    public void setId(Long id) { this.id = id; }
    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDepartamento() { return departamento; }
    public void setDepartamento(String departamento) { this.departamento = departamento; }
    public String getProvincia() { return provincia; }
    public void setProvincia(String provincia) { this.provincia = provincia; }
    public String getDistrito() { return distrito; }
    public void setDistrito(String distrito) { this.distrito = distrito; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public Boolean getEstado() { return estado; }
    public void setEstado(Boolean estado) { this.estado = estado; }
}
