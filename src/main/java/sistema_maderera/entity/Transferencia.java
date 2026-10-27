package sistema_maderera.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transferencias")
public class Transferencia {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movimiento_salida_id", nullable = false, unique = true)
    private MovimientoInventario salida;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movimiento_entrada_id", nullable = false, unique = true)
    private MovimientoInventario entrada;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal cantidad;

    @Column(nullable = false, length = 220)
    private String motivo;

    @Column(nullable = false)
    private LocalDateTime fecha;

    public Long getId() { return id; }
    public MovimientoInventario getSalida() { return salida; }
    public void setSalida(MovimientoInventario salida) { this.salida = salida; }
    public MovimientoInventario getEntrada() { return entrada; }
    public void setEntrada(MovimientoInventario entrada) { this.entrada = entrada; }
    public BigDecimal getCantidad() { return cantidad; }
    public void setCantidad(BigDecimal cantidad) { this.cantidad = cantidad; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
}
