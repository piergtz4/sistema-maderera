package sistema_maderera.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "correlativos_documento", uniqueConstraints = @UniqueConstraint(columnNames = {"sede_id", "tipo"}))
public class CorrelativoDocumento {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sede_id", nullable = false)
    private Sede sede;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private TipoDocumentoVenta tipo;

    @Column(nullable = false)
    private Long ultimo = 0L;

    public Long getId() { return id; }
    public Sede getSede() { return sede; }
    public void setSede(Sede sede) { this.sede = sede; }
    public TipoDocumentoVenta getTipo() { return tipo; }
    public void setTipo(TipoDocumentoVenta tipo) { this.tipo = tipo; }
    public Long getUltimo() { return ultimo; }
    public void setUltimo(Long ultimo) { this.ultimo = ultimo; }
}
