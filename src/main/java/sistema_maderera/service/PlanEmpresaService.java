package sistema_maderera.service;

import java.math.BigDecimal;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.PlanEmpresaForm;
import sistema_maderera.entity.Empresa;
import sistema_maderera.entity.PeriodicidadPlan;
import sistema_maderera.entity.PlanComercial;
import sistema_maderera.exception.RecursoNoEncontradoException;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.AdministradorPlataformaRepository;
import sistema_maderera.repository.EmpresaRepository;
import sistema_maderera.repository.SedeRepository;
import sistema_maderera.repository.UsuarioRepository;

@Service
@Transactional(readOnly = true)
public class PlanEmpresaService {
    public static final BigDecimal PRECIO_SEDE = new BigDecimal("15.00");
    public static final BigDecimal PRECIO_USUARIO = new BigDecimal("5.00");
    private static final Logger log = LoggerFactory.getLogger(PlanEmpresaService.class);
    private final EmpresaRepository empresas;
    private final AdministradorPlataformaRepository plataforma;
    private final SedeRepository sedes;
    private final UsuarioRepository usuarios;
    private final ValidacionService validacion;

    public PlanEmpresaService(EmpresaRepository empresas, AdministradorPlataformaRepository plataforma,
            SedeRepository sedes, UsuarioRepository usuarios, ValidacionService validacion) {
        this.empresas = empresas;
        this.plataforma = plataforma;
        this.sedes = sedes;
        this.usuarios = usuarios;
        this.validacion = validacion;
    }

    public PlanEmpresaForm formulario(Long empresaId) {
        Empresa empresa = buscar(empresaId);
        var form = new PlanEmpresaForm();
        if (empresa.getPlanComercial() != null) form.setPlan(empresa.getPlanComercial());
        form.setPeriodicidad(empresa.getPeriodicidadPlan());
        form.setSedesAdicionales(empresa.getSedesAdicionales());
        form.setUsuariosAdicionales(empresa.getUsuariosAdicionales());
        form.setVersion(empresa.getVersionPlan());
        return form;
    }

    public ResumenPlan resumen(Long empresaId) {
        Empresa empresa = buscar(empresaId);
        return empresa.getPlanComercial() == null ? null : calcular(formulario(empresaId));
    }

    // Calcula los importes finales y la capacidad contratada sin recibir precios del navegador.
    public ResumenPlan calcular(PlanEmpresaForm form) {
        validacion.validar(form);
        PlanComercial plan = form.getPlan();
        long totalSedes = (long) plan.getSedes() + form.getSedesAdicionales();
        long totalUsuarios = (long) plan.getUsuarios() + form.getUsuariosAdicionales();
        BigDecimal extraSedes = PRECIO_SEDE.multiply(BigDecimal.valueOf(form.getSedesAdicionales()));
        BigDecimal extraUsuarios = PRECIO_USUARIO.multiply(BigDecimal.valueOf(form.getUsuariosAdicionales()));
        BigDecimal mensual = plan.getPrecioMensual().add(extraSedes).add(extraUsuarios);
        BigDecimal periodo = mensual.multiply(BigDecimal.valueOf(form.getPeriodicidad().getMeses()));
        return new ResumenPlan(plan, form.getPeriodicidad(), form.getSedesAdicionales(), form.getUsuariosAdicionales(),
                totalSedes, totalUsuarios, extraSedes, extraUsuarios, mensual, periodo);
    }

    @Transactional
    public void guardar(Long empresaId, Long actorId, PlanEmpresaForm form) {
        ResumenPlan resumen = calcular(form);
        plataforma.findById(actorId).filter(a -> Boolean.TRUE.equals(a.getEstado()))
                .orElseThrow(() -> new ReglaNegocioException("Tu cuenta de plataforma no está activa. Inicia sesión nuevamente."));
        Empresa empresa = empresas.bloquearParaUsuarios(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        if (!Objects.equals(form.getVersion(), empresa.getVersionPlan())) {
            throw new ReglaNegocioException("El plan cambió mientras lo editabas. Vuelve a abrir su configuración.");
        }
        if (sedes.countByEmpresaIdAndEstadoTrue(empresaId) > resumen.sedesTotales()) {
            throw new ReglaNegocioException("La empresa tiene más sedes activas que las seleccionadas. Amplía el plan o desactiva las sedes que ya no utilice.");
        }
        if (usuarios.countByEmpresaIdAndEstadoTrue(empresaId) > resumen.usuariosTotales()) {
            throw new ReglaNegocioException("La empresa tiene más cuentas activas que las seleccionadas. Amplía el plan o desactiva las cuentas que ya no utilice.");
        }
        empresa.setPlanComercial(form.getPlan());
        empresa.setPeriodicidadPlan(form.getPeriodicidad());
        empresa.setSedesAdicionales(form.getSedesAdicionales());
        empresa.setUsuariosAdicionales(form.getUsuariosAdicionales());
        empresa.setVersionPlan(empresa.getVersionPlan() + 1);
        empresas.flush();
        log.info("Plan de empresa actualizado: empresa={}, plataforma={}, plan={}, periodicidad={}, sedesAdicionales={}, usuariosAdicionales={}",
                empresaId, actorId, form.getPlan(), form.getPeriodicidad(), form.getSedesAdicionales(), form.getUsuariosAdicionales());
    }

    private Empresa buscar(Long id) {
        return empresas.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
    }

    public record ResumenPlan(PlanComercial plan, PeriodicidadPlan periodicidad, int sedesAdicionales,
            int usuariosAdicionales, long sedesTotales, long usuariosTotales, BigDecimal importeSedes,
            BigDecimal importeUsuarios, BigDecimal totalMensual, BigDecimal totalPeriodo) {}
}
