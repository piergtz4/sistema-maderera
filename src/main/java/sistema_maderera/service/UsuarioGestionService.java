package sistema_maderera.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
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

@Service
@Transactional(readOnly = true)
public class UsuarioGestionService {
    private static final String GENERAL = "ADMINISTRADOR_GENERAL";
    private static final Set<String> ROLES = Set.of(GENERAL, "ADMINISTRADOR", "CONSULTA");
    private final UsuarioRepository usuarios;
    private final SedeRepository sedes;
    private final EmpresaRepository empresas;
    private final PasswordEncoder encoder;
    private final ValidacionService validacion;

    public UsuarioGestionService(UsuarioRepository usuarios, SedeRepository sedes,
            EmpresaRepository empresas, PasswordEncoder encoder, ValidacionService validacion) {
        this.usuarios = usuarios;
        this.sedes = sedes;
        this.empresas = empresas;
        this.encoder = encoder;
        this.validacion = validacion;
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
        return form;
    }

    @Transactional
    public Usuario guardar(Long empresaId, Long actorId, UsuarioForm form) {
        validacion.validar(form);
        Empresa empresa = empresas.bloquearParaUsuarios(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
        Usuario usuario = form.getId() == null ? new Usuario() : buscar(empresaId, form.getId());
        boolean cuentaPropia = Objects.equals(usuario.getId(), actorId);
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
        Sede sede = resolverSede(empresaId, form.getRol(), form.getSedeId());
        String password = form.getPassword();
        if (usuario.getId() == null || password != null && !password.isEmpty()) {
            validarClave(password, form.getConfirmarPassword());
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
        return usuarios.save(usuario);
    }

    @Transactional
    public boolean cambiarEstado(Long empresaId, Long actorId, Long id) {
        empresas.bloquearParaUsuarios(empresaId)
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
                && (usuario.getSede() == null || !Boolean.TRUE.equals(usuario.getSede().getEstado()))) {
            throw new ReglaNegocioException("Asigna una sede activa antes de activar esta cuenta.");
        }
        usuario.setEstado(activo);
        usuarios.save(usuario);
        return activo;
    }

    @Transactional
    public void cambiarClave(Long id, CambioClaveForm form) {
        validacion.validar(form);
        Usuario usuario = usuarios.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        if (!encoder.matches(form.getActual(), usuario.getPassword())) {
            throw new ReglaNegocioException("La contraseña actual no es correcta.");
        }
        validarClave(form.getNueva(), form.getConfirmacion());
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

    private Sede resolverSede(Long empresaId, String rol, Long sedeId) {
        if (GENERAL.equals(rol)) {
            if (sedeId != null) throw new ReglaNegocioException("El administrador general no tiene sede fija.");
            return null;
        }
        if (sedeId == null) throw new ReglaNegocioException("Selecciona una sede para este usuario.");
        return sedes.findByIdAndEmpresaIdAndEstadoTrue(sedeId, empresaId)
                .orElseThrow(() -> new ReglaNegocioException("Selecciona una sede activa de tu empresa."));
    }

    private void validarClave(String password, String confirmacion) {
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.codePoints().noneMatch(Character::isLetter)
                || password.codePoints().noneMatch(Character::isDigit)) {
            throw new ReglaNegocioException("La contraseña debe tener al menos 12 caracteres, incluir letras y números, y no superar 72 bytes.");
        }
        if (!password.equals(confirmacion)) {
            throw new ReglaNegocioException("Las contraseñas no coinciden.");
        }
    }
}
