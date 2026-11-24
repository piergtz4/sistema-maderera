package sistema_maderera.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "usuario_sedes", uniqueConstraints = @UniqueConstraint(columnNames = {"usuario_id", "sede_id"}))
public class AccesoUsuarioSede {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sede_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Sede sede;

    @Column(name = "puede_gestionar", nullable = false)
    private boolean puedeGestionar;

    public Long getId() { return id; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public Sede getSede() { return sede; }
    public void setSede(Sede sede) { this.sede = sede; }
    public boolean isPuedeGestionar() { return puedeGestionar; }
    public void setPuedeGestionar(boolean puedeGestionar) { this.puedeGestionar = puedeGestionar; }
}
