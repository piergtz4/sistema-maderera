package sistema_maderera.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import sistema_maderera.entity.PeriodicidadPlan;
import sistema_maderera.entity.PlanComercial;

public class PlanEmpresaForm {
    @NotNull(message = "Selecciona un plan.")
    private PlanComercial plan = PlanComercial.BASICO;

    @NotNull(message = "Selecciona la modalidad de pago.")
    private PeriodicidadPlan periodicidad = PeriodicidadPlan.MENSUAL;

    @NotNull(message = "Ingresa la cantidad de sedes adicionales.")
    @Min(value = 0, message = "Las sedes adicionales no pueden ser negativas.")
    private Integer sedesAdicionales = 0;

    @NotNull(message = "Ingresa la cantidad de cuentas adicionales.")
    @Min(value = 0, message = "Las cuentas adicionales no pueden ser negativas.")
    private Integer usuariosAdicionales = 0;

    @NotNull(message = "Vuelve a abrir la configuración del plan.")
    @Min(0)
    private Long version = 0L;

    public PlanComercial getPlan() { return plan; }
    public void setPlan(PlanComercial plan) { this.plan = plan; }
    public PeriodicidadPlan getPeriodicidad() { return periodicidad; }
    public void setPeriodicidad(PeriodicidadPlan periodicidad) { this.periodicidad = periodicidad; }
    public Integer getSedesAdicionales() { return sedesAdicionales; }
    public void setSedesAdicionales(Integer sedesAdicionales) { this.sedesAdicionales = sedesAdicionales; }
    public Integer getUsuariosAdicionales() { return usuariosAdicionales; }
    public void setUsuariosAdicionales(Integer usuariosAdicionales) { this.usuariosAdicionales = usuariosAdicionales; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
}
