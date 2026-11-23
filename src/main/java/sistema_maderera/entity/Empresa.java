package sistema_maderera.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "empresas")
public class Empresa {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre_comercial", nullable = false, length = 100)
    private String nombreComercial;

    @Column(name = "razon_social", length = 150)
    private String razonSocial;

    @Column(unique = true, length = 11)
    private String ruc;

    @Column(length = 30)
    private String telefono;

    @Column(length = 150)
    private String email;

    @Column(length = 255)
    private String direccion;

    @JdbcTypeCode(SqlTypes.LONGVARBINARY)
    @Column(name = "logo_datos")
    private byte[] logoDatos;

    @Column(name = "logo_tipo", length = 30)
    private String logoTipo;

    @Column(nullable = false)
    private Boolean estado = true;

    @Column(name = "version_acceso", nullable = false)
    private Long versionAcceso = 0L;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "plan_comercial", length = 20)
    private PlanComercial planComercial;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "periodicidad_plan", nullable = false, length = 10)
    private PeriodicidadPlan periodicidadPlan = PeriodicidadPlan.MENSUAL;

    @Column(name = "sedes_adicionales", nullable = false)
    private Integer sedesAdicionales = 0;

    @Column(name = "usuarios_adicionales", nullable = false)
    private Integer usuariosAdicionales = 0;

    @Column(name = "version_plan", nullable = false)
    private Long versionPlan = 0L;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getNombreComercial() { return nombreComercial; }
    public void setNombreComercial(String nombreComercial) { this.nombreComercial = nombreComercial; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }
    public String getRuc() { return ruc; }
    public void setRuc(String ruc) { this.ruc = ruc; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public byte[] getLogoDatos() { return logoDatos; }
    public void setLogoDatos(byte[] logoDatos) { this.logoDatos = logoDatos; }
    public String getLogoTipo() { return logoTipo; }
    public void setLogoTipo(String logoTipo) { this.logoTipo = logoTipo; }
    public Boolean getEstado() { return estado; }
    public void setEstado(Boolean estado) { this.estado = estado; }
    public Long getVersionAcceso() { return versionAcceso; }
    public void setVersionAcceso(Long versionAcceso) { this.versionAcceso = versionAcceso; }
    public PlanComercial getPlanComercial() { return planComercial; }
    public void setPlanComercial(PlanComercial planComercial) { this.planComercial = planComercial; }
    public PeriodicidadPlan getPeriodicidadPlan() { return periodicidadPlan; }
    public void setPeriodicidadPlan(PeriodicidadPlan periodicidadPlan) { this.periodicidadPlan = periodicidadPlan; }
    public Integer getSedesAdicionales() { return sedesAdicionales; }
    public void setSedesAdicionales(Integer sedesAdicionales) { this.sedesAdicionales = sedesAdicionales; }
    public Integer getUsuariosAdicionales() { return usuariosAdicionales; }
    public void setUsuariosAdicionales(Integer usuariosAdicionales) { this.usuariosAdicionales = usuariosAdicionales; }
    public Long getVersionPlan() { return versionPlan; }
    public void setVersionPlan(Long versionPlan) { this.versionPlan = versionPlan; }
}
