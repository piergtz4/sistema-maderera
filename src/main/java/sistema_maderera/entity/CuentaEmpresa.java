package sistema_maderera.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "cuentas_empresa")
public class CuentaEmpresa {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "empresa_id", nullable = false)
    private Empresa empresa;
    @Column(nullable = false, length = 80)
    private String banco;
    @Column(nullable = false, length = 30)
    private String numero;
    @Column(length = 9)
    private String celularYape;
    @Column(nullable = false)
    private boolean activa = true;
    public Long getId() { return id; }
    public Empresa getEmpresa() { return empresa; }
    public void setEmpresa(Empresa empresa) { this.empresa = empresa; }
    public String getBanco() { return banco; }
    public void setBanco(String banco) { this.banco = banco; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getCelularYape() { return celularYape; }
    public void setCelularYape(String celularYape) { this.celularYape = celularYape; }
    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }
    public String getEtiqueta() { return banco + " · Soles · …" + numero.substring(Math.max(0, numero.length() - 4)); }
}
