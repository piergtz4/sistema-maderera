package sistema_maderera.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import sistema_maderera.entity.TipoComprobanteCompra;

public class CompraForm {
    private Long id;
    private Long version;
    @NotNull(message = "Selecciona un proveedor.")
    private Long proveedorId;
    @NotNull(message = "Ingresa la fecha del comprobante.")
    @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
    private LocalDate fecha;
    @NotNull(message = "Selecciona el tipo de comprobante.")
    private TipoComprobanteCompra tipo = TipoComprobanteCompra.FACTURA;
    @NotBlank(message = "Ingresa la referencia del comprobante del proveedor.")
    @Size(max = 60, message = "Referencia: máximo 60 caracteres.")
    private String referencia;
    @Size(max = 2000, message = "Detalles: máximo 2000 caracteres.")
    private String detalles;
    @NotNull
    @Size(min = 1, max = 100, message = "Agrega entre 1 y 100 productos.")
    private List<@NotNull @Valid DetalleCompraForm> lineas = new ArrayList<>();
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public Long getProveedorId() { return proveedorId; }
    public void setProveedorId(Long proveedorId) { this.proveedorId = proveedorId; }
    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }
    public TipoComprobanteCompra getTipo() { return tipo; }
    public void setTipo(TipoComprobanteCompra tipo) { this.tipo = tipo; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public String getDetalles() { return detalles; }
    public void setDetalles(String detalles) { this.detalles = detalles; }
    public List<DetalleCompraForm> getLineas() { return lineas; }
    public void setLineas(List<DetalleCompraForm> lineas) { this.lineas = lineas; }
}
