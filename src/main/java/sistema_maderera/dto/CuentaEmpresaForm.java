package sistema_maderera.dto;

import jakarta.validation.constraints.*;

public class CuentaEmpresaForm {
    @NotBlank @Size(max = 80)
    private String banco;
    @NotBlank @Pattern(regexp = "[0-9]{10,30}", message = "Cuenta: ingresa de 10 a 30 dígitos, sin espacios ni guiones.")
    private String numero;
    @Pattern(regexp = "^$|9[0-9]{8}", message = "Yape: ingresa un celular de 9 dígitos que empiece con 9.")
    private String celularYape;
    public String getBanco() { return banco; }
    public void setBanco(String banco) { this.banco = banco; }
    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getCelularYape() { return celularYape; }
    public void setCelularYape(String celularYape) { this.celularYape = celularYape; }
}
