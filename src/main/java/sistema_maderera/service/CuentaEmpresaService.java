package sistema_maderera.service;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.CuentaEmpresaForm;
import sistema_maderera.entity.CuentaEmpresa;
import sistema_maderera.exception.*;
import sistema_maderera.repository.*;

@Service
@Transactional(readOnly = true)
public class CuentaEmpresaService {
    public static final List<String> BANCOS = List.of("BCP", "INTERBANK", "BBVA", "SCOTIABANK", "BANCO DE LA NACION", "BANBIF", "PICHINCHA");
    private final CuentaEmpresaRepository cuentas;
    private final EmpresaRepository empresas;
    private final ValidacionService validacion;
    public CuentaEmpresaService(CuentaEmpresaRepository cuentas, EmpresaRepository empresas, ValidacionService validacion) {
        this.cuentas = cuentas; this.empresas = empresas; this.validacion = validacion;
    }
    public List<CuentaEmpresa> listar(Long empresaId) { return cuentas.findByEmpresaIdOrderByIdAsc(empresaId); }
    public CuentaEmpresa buscar(Long empresaId, Long id) {
        return cuentas.findByIdAndEmpresaId(id, empresaId).orElseThrow(() -> new RecursoNoEncontradoException("Cuenta no encontrada en tu empresa."));
    }
    @Transactional
    public void agregar(Long empresaId, CuentaEmpresaForm form) {
        validacion.validar(form);
        var empresa = empresas.bloquearParaUsuarios(empresaId).orElseThrow();
        if (!BANCOS.contains(form.getBanco())) throw new ReglaNegocioException("Selecciona un banco de la lista.");
        String celular = form.getCelularYape() == null || form.getCelularYape().isBlank() ? null : form.getCelularYape();
        if (celular != null && !"BCP".equals(form.getBanco())) throw new ReglaNegocioException("La cuenta vinculada a Yape debe ser BCP.");
        var actuales = listar(empresaId);
        if (actuales.stream().anyMatch(c -> c.getBanco().equals(form.getBanco()) && c.getNumero().equals(form.getNumero())))
            throw new ReglaNegocioException("Esa cuenta ya está registrada. Puedes reactivarla si está inactiva.");
        if (celular != null && actuales.stream().anyMatch(c -> c.isActiva() && c.getCelularYape() != null))
            throw new ReglaNegocioException("Ya existe un Yape activo. Desactívalo antes de registrar otro.");
        CuentaEmpresa cuenta = new CuentaEmpresa();
        cuenta.setEmpresa(empresa); cuenta.setBanco(form.getBanco()); cuenta.setNumero(form.getNumero()); cuenta.setCelularYape(celular);
        cuentas.save(cuenta);
    }
    @Transactional
    public void estado(Long empresaId, Long id, boolean activa) {
        empresas.bloquearParaUsuarios(empresaId).orElseThrow();
        CuentaEmpresa cuenta = buscar(empresaId, id);
        if (activa && cuenta.getCelularYape() != null && listar(empresaId).stream()
                .anyMatch(c -> !c.getId().equals(id) && c.isActiva() && c.getCelularYape() != null))
            throw new ReglaNegocioException("Solo puede haber un Yape activo por empresa.");
        cuenta.setActiva(activa);
    }
}
