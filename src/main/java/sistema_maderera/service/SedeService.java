package sistema_maderera.service;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.SedeForm;
import sistema_maderera.entity.Sede;
import sistema_maderera.exception.RecursoNoEncontradoException;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.EmpresaRepository;
import sistema_maderera.repository.SedeRepository;
import sistema_maderera.repository.UsuarioRepository;
import sistema_maderera.repository.CorrelativoDocumentoRepository;
import sistema_maderera.repository.DocumentoVentaRepository;
import sistema_maderera.repository.InventarioRepository;
import sistema_maderera.repository.CompraRepository;
import sistema_maderera.repository.AccesoUsuarioSedeRepository;

@Service
@Transactional(readOnly = true)
public class SedeService {
    private final SedeRepository sedes;
    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final ValidacionService validacion;
    private final CorrelativoDocumentoRepository correlativos;
    private final DocumentoVentaRepository documentos;
    private final PlanEmpresaService planes;
    private final AccesoSedeService accesos;
    private final InventarioRepository inventarios;
    private final CompraRepository compras;
    private final AccesoUsuarioSedeRepository asignaciones;

    public SedeService(SedeRepository sedes, EmpresaRepository empresas,
            UsuarioRepository usuarios, ValidacionService validacion,
            CorrelativoDocumentoRepository correlativos, DocumentoVentaRepository documentos, PlanEmpresaService planes,
            AccesoSedeService accesos, InventarioRepository inventarios, CompraRepository compras,
            AccesoUsuarioSedeRepository asignaciones) {
        this.sedes = sedes;
        this.empresas = empresas;
        this.usuarios = usuarios;
        this.validacion = validacion;
        this.correlativos = correlativos;
        this.documentos = documentos;
        this.planes = planes;
        this.accesos = accesos;
        this.inventarios = inventarios;
        this.compras = compras;
        this.asignaciones = asignaciones;
    }

    public List<Sede> listar(Long empresaId) {
        return sedes.findByEmpresaIdOrderByEstadoDescNombreAsc(empresaId);
    }

    public SedeForm formulario(Long empresaId, Long id) {
        Sede sede = buscar(empresaId, id);
        SedeForm form = new SedeForm();
        form.setId(sede.getId());
        form.setCodigoDocumentos(sede.getCodigoDocumentos());
        form.setNombre(sede.getNombre());
        form.setDepartamento(sede.getDepartamento());
        form.setProvincia(sede.getProvincia());
        form.setDistrito(sede.getDistrito());
        form.setDireccion(sede.getDireccion());
        form.setTelefono(sede.getTelefono());
        return form;
    }

    @Transactional
    public Sede guardar(Long empresaId, SedeForm form) {
        validacion.validar(form);
        var empresa = empresas.bloquearParaUsuarios(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        String nombre = limpiar(form.getNombre());
        if (sedes.findByEmpresaIdAndNombreIgnoreCase(empresaId, nombre)
                .filter(s -> !Objects.equals(s.getId(), form.getId())).isPresent()) {
            throw new ReglaNegocioException("Ya existe una sede con ese nombre en la empresa.");
        }
        Sede sede = form.getId() == null ? new Sede() : sedes.bloquearParaNumeracion(form.getId())
                .filter(s -> s.getEmpresa().getId().equals(empresaId))
                .orElseThrow(() -> new RecursoNoEncontradoException("Sede no encontrada en tu empresa."));
        if (form.getId() == null) {
            planes.validarCupoSede(empresa);
            sede.setEmpresa(empresa);
        }
        Integer codigo = form.getCodigoDocumentos() != null ? form.getCodigoDocumentos()
                : sede.getId() == null ? sedes.ultimoCodigoDocumentos(empresaId) + 1 : sede.getCodigoDocumentos();
        if (codigo < 1 || codigo > 999) throw new ReglaNegocioException("Selecciona un identificador entre 1 y 999.");
        if (sedes.findByEmpresaIdAndCodigoDocumentos(empresaId, codigo)
                .filter(s -> !Objects.equals(s.getId(), sede.getId())).isPresent()) {
            throw new ReglaNegocioException("Ese identificador de documentos ya pertenece a otra sede.");
        }
        if (sede.getId() != null && !codigo.equals(sede.getCodigoDocumentos())
                && (correlativos.existsBySedeIdAndUltimoGreaterThan(sede.getId(), 0L)
                    || documentos.existsBySedeIdAndCorrelativoIsNotNull(sede.getId()))) {
            throw new ReglaNegocioException("La sede ya tiene numeración utilizada; sus series se conservan.");
        }
        sede.setCodigoDocumentos(codigo);
        sede.setNombre(nombre);
        sede.setDepartamento(limpiar(form.getDepartamento()));
        sede.setProvincia(limpiar(form.getProvincia()));
        sede.setDistrito(limpiar(form.getDistrito()));
        sede.setDireccion(limpiarOpcional(form.getDireccion()));
        sede.setTelefono(limpiarOpcional(form.getTelefono()));
        return sedes.save(sede);
    }

    @Transactional
    public boolean cambiarEstado(Long empresaId, Long id) {
        var empresa = empresas.bloquearParaUsuarios(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        Sede sede = buscar(empresaId, id);
        if (Boolean.TRUE.equals(sede.getEstado()) && sedes.countByEmpresaIdAndEstadoTrue(empresaId) <= 1) {
            throw new ReglaNegocioException("No puedes desactivar la última sede activa de la empresa.");
        }
        boolean activo = !Boolean.TRUE.equals(sede.getEstado());
        if (activo) planes.validarCupoSede(empresa);
        sede.setEstado(activo);
        sedes.save(sede);
        if (!activo) desactivarCuentasSinSede(empresaId, id);
        return activo;
    }

    public String nombreParaEliminar(Long empresaId, Long id) {
        Sede sede = buscar(empresaId, id);
        validarEliminacion(sede, empresaId);
        return sede.getNombre();
    }

    @Transactional
    public void eliminar(Long empresaId, Long actorId, Long id, boolean confirmar) {
        empresas.bloquearParaUsuarios(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        usuarios.findByIdAndEmpresaId(actorId, empresaId)
                .filter(u -> Boolean.TRUE.equals(u.getEstado())
                        && Boolean.TRUE.equals(u.getEmpresa().getEstado()) && "ADMINISTRADOR_GENERAL".equals(u.getRol()))
                .orElseThrow(() -> new ReglaNegocioException("Solo el administrador general activo puede eliminar sedes."));
        Sede sede = buscar(empresaId, id);
        validarEliminacion(sede, empresaId);
        if (!confirmar) throw new ReglaNegocioException("Confirma que deseas eliminar esta sede.");
        sedes.delete(sede);
        sedes.flush();
    }

    private void validarEliminacion(Sede sede, Long empresaId) {
        if (Boolean.TRUE.equals(sede.getEstado()) && sedes.countByEmpresaIdAndEstadoTrue(empresaId) <= 1)
            throw new ReglaNegocioException("No puedes eliminar la última sede activa de la empresa.");
        if (usuarios.existsBySedeId(sede.getId()) || asignaciones.existsBySedeId(sede.getId()))
            throw new ReglaNegocioException("No puedes eliminar esta sede porque tiene usuarios asignados. Reasigna sus sedes autorizadas antes de eliminarla.");
        if (inventarios.existsBySedeId(sede.getId()) || compras.existsBySedeId(sede.getId())
                || documentos.existsBySedeId(sede.getId()) || correlativos.existsBySedeId(sede.getId()))
            throw new ReglaNegocioException("No puedes eliminar esta sede porque tiene inventario, compras, ventas o numeración asociados. Puedes desactivarla para conservar su historial.");
    }

    private void desactivarCuentasSinSede(Long empresaId, Long sedeId) {
        var cuentas = usuarios.findByEmpresaIdAndSedeIdAndEstadoTrue(empresaId, sedeId);
        cuentas.stream().filter(cuenta -> !accesos.tieneAsignacionActiva(cuenta)).forEach(cuenta -> {
            cuenta.setEstado(false);
            cuenta.setVersionAcceso(cuenta.getVersionAcceso() + 1);
        });
        usuarios.saveAll(cuentas);
    }

    private Sede buscar(Long empresaId, Long id) {
        return sedes.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sede no encontrada en tu empresa."));
    }

    private String limpiar(String valor) { return valor.trim().replaceAll("\\s+", " "); }
    private String limpiarOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : limpiar(valor);
    }
}
