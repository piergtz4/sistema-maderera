package sistema_maderera.dto;

import jakarta.validation.constraints.*;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import sistema_maderera.entity.TipoDocumentoVenta;

public class DocumentoVentaForm {
    private Long id;
    private Long version;
    @NotNull(message = "Selecciona el tipo de documento.")
    private TipoDocumentoVenta tipo = TipoDocumentoVenta.BOLETA;
    private Long clienteId;
    @Size(max = 2000, message = "Detalles: máximo 2000 caracteres.")
    private String detalles;
    @Valid
    @NotNull
    @Size(max = 100, message = "Se permiten hasta 100 productos por documento.")
    private List<@NotNull DetalleVentaForm> lineas = new ArrayList<>();
    @Valid
    @NotNull
    @Size(max = 30, message = "Se permiten hasta 30 abonos por documento.")
    private List<@NotNull PagoVentaForm> pagos = new ArrayList<>();
    public List<PagoVentaForm> getPagos() { return pagos; }
    public void setPagos(List<PagoVentaForm> pagos) { this.pagos = pagos; }

    public List<DetalleVentaForm> getLineas() { return lineas; }
    public void setLineas(List<DetalleVentaForm> lineas) { this.lineas = lineas; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public TipoDocumentoVenta getTipo() { return tipo; }
    public void setTipo(TipoDocumentoVenta tipo) { this.tipo = tipo; }
    public Long getClienteId() { return clienteId; }
    public void setClienteId(Long clienteId) { this.clienteId = clienteId; }
    public String getDetalles() { return detalles; }
    public void setDetalles(String detalles) { this.detalles = detalles; }
}
