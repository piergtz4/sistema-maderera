package sistema_maderera.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.PlataformaAdministradorEmpresaForm;
import sistema_maderera.dto.RestablecerClavePlataformaForm;
import sistema_maderera.dto.UsuarioForm;
import sistema_maderera.entity.AdministradorPlataforma;
import sistema_maderera.entity.Usuario;
import sistema_maderera.exception.RecursoNoEncontradoException;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.AdministradorPlataformaRepository;
import sistema_maderera.repository.EmpresaRepository;
import sistema_maderera.repository.UsuarioRepository;

@Service
@Transactional(readOnly = true)
public class PlataformaAdministradorEmpresaService {
    private static final String GENERAL = "ADMINISTRADOR_GENERAL";
    private static final Logger log = LoggerFactory.getLogger(PlataformaAdministradorEmpresaService.class);
    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final AdministradorPlataformaRepository plataforma;
    private final UsuarioGestionService gestionUsuarios;
    private final PasswordEncoder encoder;
    private final ValidacionService validacion;
    private final PoliticaContrasenaService contrasenas;
    private final AccesoSedeService accesos;

    public PlataformaAdministradorEmpresaService(EmpresaRepository empresas, UsuarioRepository usuarios,
            AdministradorPlataformaRepository plataforma, UsuarioGestionService gestionUsuarios,
            PasswordEncoder encoder, ValidacionService validacion, PoliticaContrasenaService contrasenas, AccesoSedeService accesos) {
        this.empresas = empresas;
        this.usuarios = usuarios;
        this.plataforma = plataforma;
        this.gestionUsuarios = gestionUsuarios;
        this.encoder = encoder;
        this.validacion = validacion;
        this.contrasenas = contrasenas;
        this.accesos = accesos;
    }

    public List<AdministradorVista> listar(Long empresaId) {
        return usuarios.findByEmpresaIdAndRolOrderByNombreAsc(empresaId, GENERAL).stream()
                .map(u -> new AdministradorVista(u.getId(), u.getNombre(), u.getUsuario(), Boolean.TRUE.equals(u.getEstado())))
                .toList();
    }

    public List<UsuarioVista> listarUsuarios(Long empresaId) {
        return usuarios.findByEmpresaIdOrderByNombreAsc(empresaId).stream()
                .map(u -> new UsuarioVista(u.getNombre(), u.getUsuario(), etiquetaRol(u.getRol()),
                        accesos.descripcion(u),
                        Boolean.TRUE.equals(u.getEstado())))
                .toList();
    }

    public PlataformaAdministradorEmpresaForm formulario(Long empresaId, Long id) {
        Usuario usuario = buscar(empresaId, id);
        var form = new PlataformaAdministradorEmpresaForm();
        form.setNombre(usuario.getNombre());
        form.setUsuario(usuario.getUsuario());
        return form;
    }

    @Transactional
    public void editar(Long empresaId, Long id, Long actorId, PlataformaAdministradorEmpresaForm form) {
        validacion.validar(form);
        exigirActorActivo(actorId);
        bloquearEmpresa(empresaId);
        Usuario usuario = buscar(empresaId, id);
        UsuarioForm datos = new UsuarioForm();
        datos.setId(usuario.getId());
        datos.setNombre(form.getNombre());
        datos.setUsuario(form.getUsuario());
        datos.setRol(GENERAL);
        gestionUsuarios.guardar(empresaId, null, datos);
        usuarios.flush();
        log.info("Administrador de empresa editado: empresa={}, usuario={}, plataforma={}", empresaId, id, actorId);
    }

    @Transactional
    public void cambiarEstado(Long empresaId, Long id, Long actorId, boolean activo) {
        exigirActorActivo(actorId);
        bloquearEmpresa(empresaId);
        Usuario usuario = buscar(empresaId, id);
        if (Boolean.TRUE.equals(usuario.getEstado()) != activo) {
            gestionUsuarios.cambiarEstado(empresaId, null, id);
            log.info("Acceso de administrador de empresa actualizado: empresa={}, usuario={}, activo={}, plataforma={}",
                    empresaId, id, activo, actorId);
        }
    }

    @Transactional
    public void restablecerClave(Long empresaId, Long id, Long actorId, RestablecerClavePlataformaForm form) {
        validacion.validar(form);
        var actor = exigirActorActivo(actorId);
        if (form.getClavePlataforma().getBytes(StandardCharsets.UTF_8).length > 72
                || !encoder.matches(form.getClavePlataforma(), actor.getPassword())) {
            throw new ReglaNegocioException("Tu contraseña de administrador de plataforma no es correcta.");
        }
        contrasenas.validar(form.getNueva(), form.getConfirmacion());
        bloquearEmpresa(empresaId);
        Usuario usuario = buscar(empresaId, id);
        if (encoder.matches(form.getNueva(), usuario.getPassword())) {
            throw new ReglaNegocioException("La nueva contraseña debe ser diferente de la actual.");
        }
        usuario.setPassword(encoder.encode(form.getNueva()));
        usuarios.saveAndFlush(usuario);
        log.info("Contraseña de administrador de empresa restablecida: empresa={}, usuario={}, plataforma={}", empresaId, id, actorId);
    }

    private Usuario buscar(Long empresaId, Long id) {
        return usuarios.findByIdAndEmpresaIdAndRol(id, empresaId, GENERAL)
                .orElseThrow(() -> new RecursoNoEncontradoException("Administrador no encontrado en esta empresa."));
    }

    private void bloquearEmpresa(Long empresaId) {
        empresas.bloquearParaUsuarios(empresaId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Empresa no encontrada."));
    }

    private AdministradorPlataforma exigirActorActivo(Long id) {
        return plataforma.findById(id).filter(a -> Boolean.TRUE.equals(a.getEstado()))
                .orElseThrow(() -> new ReglaNegocioException("Tu cuenta de plataforma no está activa. Inicia sesión nuevamente."));
    }

    public record AdministradorVista(Long id, String nombre, String usuario, boolean activo) {}

    public record UsuarioVista(String nombre, String usuario, String rol, String sede, boolean activo) {}

    private String etiquetaRol(String rol) {
        return switch (rol) {
            case GENERAL -> "Administrador general";
            case "ADMINISTRADOR" -> "Administrador de sede";
            case "CONTADOR" -> "Contador";
            default -> rol;
        };
    }
}
