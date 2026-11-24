package sistema_maderera.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.TreeMap;
import java.util.LinkedHashMap;
import sistema_maderera.entity.AccesoUsuarioSede;
import sistema_maderera.repository.AccesoUsuarioSedeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.CambioClaveForm;
import sistema_maderera.dto.UsuarioForm;
import sistema_maderera.entity.Empresa;
import sistema_maderera.entity.Sede;
import sistema_maderera.entity.Usuario;
import sistema_maderera.exception.RecursoNoEncontradoException;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.EmpresaRepository;
import sistema_maderera.repository.SedeRepository;
import sistema_maderera.repository.UsuarioRepository;
import sistema_maderera.repository.MovimientoInventarioRepository;
import sistema_maderera.repository.CompraRepository;
import sistema_maderera.repository.DocumentoVentaRepository;

@Service
@Transactional(readOnly = true)
public class UsuarioGestionService {
    private static final String GENERAL = "ADMINISTRADOR_GENERAL";
    private static final Set<String> ROLES = Set.of(GENERAL, "ADMINISTRADOR", "CONTADOR");
    private final UsuarioRepository usuarios;
    private final SedeRepository sedes;
    private final EmpresaRepository empresas;
    private final PasswordEncoder encoder;
    private final ValidacionService validacion;
    private final PoliticaContrasenaService contrasenas;
    private final PlanEmpresaService planes;
    private final AccesoSedeService accesos;
    private final AccesoUsuarioSedeRepository asignaciones;
    private final MovimientoInventarioRepository movimientos;
    private final CompraRepository compras;
    private final DocumentoVentaRepository documentos;

    public UsuarioGestionService(UsuarioRepository usuarios, SedeRepository sedes,
            EmpresaRepository empresas, PasswordEncoder encoder, ValidacionService validacion,
            PoliticaContrasenaService contrasenas, PlanEmpresaService planes,
            AccesoSedeService accesos, AccesoUsuarioSedeRepository asignaciones,
            MovimientoInventarioRepository movimientos, CompraRepository compras, DocumentoVentaRepository documentos) {
        this.usuarios = usuarios;
        this.sedes = sedes;
        this.empresas = empresas;
        this.encoder = encoder;
        this.validacion = validacion;
        this.contrasenas = contrasenas;
        this.planes = planes;
        this.accesos = accesos;
        this.asignaciones = asignaciones;
        this.movimientos = movimientos;
        this.compras = compras;
        this.documentos = documentos;
    }

    public List<Usuario> listar(Long empresaId) {
        return usuarios.findByEmpresaIdOrderByNombreAsc(empresaId);
    }

    public UsuarioForm formulario(Long empresaId, Long id) {
        Usuario usuario = buscar(empresaId, id);
        UsuarioForm form = new UsuarioForm();
        form.setId(usuario.getId());
        form.setNombre(usuario.getNombre());
        form.setUsuario(usuario.getUsuario());
        form.setRol(usuario.getRol());
        form.setSedeId(usuario.getSede() == null ? null : usuario.getSede().getId());
        if (!GENERAL.equals(usuario.getRol())) {
            form.setSedeIds(accesos.asignados(usuario).stream().map(a -> a.sede().getId()).toList());
            form.setSedesGestionIds(accesos.asignados(usuario).stream().filter(AccesoSedeService.PermisoSede::gestion)
                    .map(a -> a.sede().getId()).toList());
        }
        return form;
    }

    @Transactional
    public Usuario guardar(Long empresaId, Long actorId, UsuarioForm form) {
        validacion.validar(form);
        Empresa empresa = empresas.bloquearParaUsuarios(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        Usuario usuario = form.getId() == null ? new Usuario() : buscar(empresaId, form.getId());
        boolean cuentaPropia = usuario.getId() != null && Objects.equals(usuario.getId(), actorId);
        if (cuentaPropia && (form.getPassword() != null && !form.getPassword().isEmpty()
                || form.getConfirmarPassword() != null && !form.getConfirmarPassword().isEmpty())) {
            throw new ReglaNegocioException("Para cambiar tu contraseña, usa Mi cuenta e ingresa la contraseña actual.");
        }
        String login = form.getUsuario().trim().toLowerCase(Locale.ROOT);
        if (usuarios.findByUsuarioIgnoreCase(login)
                .filter(u -> !Objects.equals(u.getId(), form.getId())).isPresent()) {
            throw new ReglaNegocioException("Ese nombre de usuario ya está registrado.");
        }
        if (!ROLES.contains(form.getRol())) throw new ReglaNegocioException("Selecciona un rol válido.");
        if (cuentaPropia && !GENERAL.equals(form.getRol())) {
            throw new ReglaNegocioException("No puedes quitarte tu propio acceso de administrador general.");
        }
        if (usuario.getId() != null && Boolean.TRUE.equals(usuario.getEstado())
                && GENERAL.equals(usuario.getRol()) && !GENERAL.equals(form.getRol())
                && usuarios.countByEmpresaIdAndRolAndEstadoTrue(empresaId, GENERAL) <= 1) {
            throw new ReglaNegocioException("Debe quedar al menos un administrador general activo.");
        }
        if (GENERAL.equals(form.getRol()) && !GENERAL.equals(usuario.getRol())
                && usuarios.generalesParaEdicion(empresaId).stream().anyMatch(u -> !Objects.equals(u.getId(), usuario.getId()))) {
            throw new ReglaNegocioException("La empresa ya tiene un administrador general. Crea una cuenta de contador o administrador de sede.");
        }
        Map<Sede, Boolean> permisos = resolverSedes(empresaId, form);
        Map<Long, Boolean> anteriores = new TreeMap<>();
        if (usuario.getId() != null && !GENERAL.equals(usuario.getRol()))
            accesos.asignados(usuario).forEach(a -> anteriores.put(a.sede().getId(), a.gestion()));
        Map<Long, Boolean> nuevos = new TreeMap<>();
        permisos.forEach((s, gestion) -> nuevos.put(s.getId(), gestion));
        boolean cambiaAcceso = !Objects.equals(usuario.getRol(), form.getRol()) || !anteriores.equals(nuevos);
        Sede sede = permisos.keySet().stream().findFirst().orElse(null);
        if (usuario.getId() == null) planes.validarCupoUsuario(empresa);
        String password = form.getPassword();
        if (usuario.getId() == null || password != null && !password.isEmpty()) {
            contrasenas.validar(password, form.getConfirmarPassword());
            if (usuario.getId() != null) {
                Usuario administrador = buscar(empresaId, actorId);
                String claveAdministrador = form.getClaveAdministrador();
                if (claveAdministrador == null || !encoder.matches(claveAdministrador, administrador.getPassword())) {
                    throw new ReglaNegocioException("Tu contraseña de administrador no es correcta.");
                }
            }
            usuario.setPassword(encoder.encode(password));
        } else if (form.getConfirmarPassword() != null && !form.getConfirmarPassword().isEmpty()) {
            throw new ReglaNegocioException("Ingresa la nueva contraseña antes de confirmarla.");
        }
        if (usuario.getId() == null) {
            usuario.setEmpresa(empresa);
        }
        usuario.setNombre(form.getNombre().trim().replaceAll("\\s+", " "));
        usuario.setUsuario(login);
        usuario.setRol(form.getRol());
        usuario.setSede(sede);
        if (usuario.getId() != null && cambiaAcceso) usuario.setVersionAcceso(usuario.getVersionAcceso() + 1);
        usuarios.save(usuario);
        asignaciones.deleteByUsuarioId(usuario.getId());
        asignaciones.flush();
        permisos.forEach((asignada, gestion) -> {
            AccesoUsuarioSede acceso = new AccesoUsuarioSede();
            acceso.setUsuario(usuario);
            acceso.setSede(asignada);
            acceso.setPuedeGestionar(gestion);
            asignaciones.save(acceso);
        });
        return usuario;
    }

    @Transactional
    public boolean cambiarEstado(Long empresaId, Long actorId, Long id) {
        Empresa empresa = empresas.bloquearParaUsuarios(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        Usuario usuario = buscar(empresaId, id);
        if (Objects.equals(actorId, id)) {
            throw new ReglaNegocioException("No puedes desactivar tu propia cuenta.");
        }
        if (Boolean.TRUE.equals(usuario.getEstado()) && GENERAL.equals(usuario.getRol())
                && usuarios.countByEmpresaIdAndRolAndEstadoTrue(empresaId, GENERAL) <= 1) {
            throw new ReglaNegocioException("Debe quedar al menos un administrador general activo.");
        }
        boolean activo = !Boolean.TRUE.equals(usuario.getEstado());
        if (activo && !GENERAL.equals(usuario.getRol())
                && !accesos.tieneAsignacionActiva(usuario)) {
            throw new ReglaNegocioException("Asigna una sede activa antes de activar esta cuenta.");
        }
        if (activo) planes.validarCupoUsuario(empresa);
        usuario.setEstado(activo);
        usuario.setVersionAcceso(usuario.getVersionAcceso() + 1);
        usuarios.save(usuario);
        return activo;
    }

    public String nombreParaEliminar(Long empresaId, Long actorId, Long id) {
        Usuario usuario = buscar(empresaId, id);
        validarEliminacion(usuario, actorId, empresaId);
        return usuario.getNombre();
    }

    @Transactional
    public void eliminar(Long empresaId, Long actorId, Long id, boolean confirmar) {
        empresas.bloquearParaUsuarios(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        Usuario actor = buscar(empresaId, actorId);
        if (!GENERAL.equals(actor.getRol()) || !Boolean.TRUE.equals(actor.getEstado()) || !actor.getEmpresa().getEstado())
            throw new ReglaNegocioException("Solo el administrador general activo puede eliminar usuarios.");
        Usuario usuario = buscar(empresaId, id);
        validarEliminacion(usuario, actorId, empresaId);
        if (!confirmar) throw new ReglaNegocioException("Confirma que deseas eliminar este usuario.");
        asignaciones.deleteByUsuarioId(id);
        asignaciones.flush();
        usuarios.delete(usuario);
        usuarios.flush();
    }

    private void validarEliminacion(Usuario usuario, Long actorId, Long empresaId) {
        if (Objects.equals(actorId, usuario.getId()))
            throw new ReglaNegocioException("No puedes eliminar tu propia cuenta.");
        if (GENERAL.equals(usuario.getRol()) && Boolean.TRUE.equals(usuario.getEstado())
                && usuarios.countByEmpresaIdAndRolAndEstadoTrue(empresaId, GENERAL) <= 1)
            throw new ReglaNegocioException("Debe quedar al menos un administrador general activo.");
        if (movimientos.existsByUsuarioId(usuario.getId()) || compras.countReferenciasUsuario(usuario.getId()) > 0
                || documentos.countReferenciasUsuario(usuario.getId()) > 0)
            throw new ReglaNegocioException("No puedes eliminar este usuario porque tiene movimientos, compras o ventas asociados. Puedes desactivarlo para conservar su historial.");
    }

    @Transactional
    public void cambiarClave(Long id, CambioClaveForm form) {
        validacion.validar(form);
        Usuario usuario = usuarios.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        if (!encoder.matches(form.getActual(), usuario.getPassword())) {
            throw new ReglaNegocioException("La contraseña actual no es correcta.");
        }
        contrasenas.validar(form.getNueva(), form.getConfirmacion());
        if (encoder.matches(form.getNueva(), usuario.getPassword())) {
            throw new ReglaNegocioException("La nueva contraseña debe ser diferente de la actual.");
        }
        usuario.setPassword(encoder.encode(form.getNueva()));
        usuarios.save(usuario);
    }

    private Usuario buscar(Long empresaId, Long id) {
        return usuarios.findByIdAndEmpresaId(id, empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado en tu empresa."));
    }

    private Map<Sede, Boolean> resolverSedes(Long empresaId, UsuarioForm form) {
        List<Long> ids = form.getSedeIds() == null ? List.of() : form.getSedeIds();
        List<Long> gestion = form.getSedesGestionIds() == null ? List.of() : form.getSedesGestionIds();
        if (GENERAL.equals(form.getRol())) {
            if (form.getSedeId() != null || !ids.isEmpty() || !gestion.isEmpty())
                throw new ReglaNegocioException("El administrador general tiene acceso a todas las sedes.");
            return Map.of();
        }
        // Compatibilidad con los formularios anteriores que enviaban una sola sede.
        if (ids.isEmpty() && form.getSedeId() != null) {
            ids = List.of(form.getSedeId());
            if ("ADMINISTRADOR".equals(form.getRol())) gestion = ids;
        }
        if (ids.isEmpty()) throw new ReglaNegocioException("Selecciona una sede como mínimo para este usuario.");
        if (ids.stream().anyMatch(Objects::isNull) || gestion.stream().anyMatch(Objects::isNull) || !ids.containsAll(gestion))
            throw new ReglaNegocioException("Solo puedes dar gestión sobre las sedes autorizadas para consulta.");
        Map<Sede, Boolean> permisos = new LinkedHashMap<>();
        for (Long id : ids.stream().distinct().sorted().toList()) {
            Sede sede = sedes.findByIdAndEmpresaIdAndEstadoTrue(id, empresaId)
                    .orElseThrow(() -> new ReglaNegocioException("Selecciona una sede activa de tu empresa para cada permiso."));
            permisos.put(sede, gestion.contains(id));
        }
        return permisos;
    }

    public Map<Long, String> descripcionesSedes(Long empresaId) {
        return listar(empresaId).stream().collect(java.util.stream.Collectors.toMap(Usuario::getId, accesos::descripcion));
    }

    public Map<Long, Boolean> cuentasConSedeActiva(Long empresaId) {
        return listar(empresaId).stream().collect(java.util.stream.Collectors.toMap(Usuario::getId, accesos::tieneAsignacionActiva));
    }

}
