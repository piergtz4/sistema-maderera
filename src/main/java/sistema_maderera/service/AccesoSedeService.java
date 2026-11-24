package sistema_maderera.service;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.entity.Sede;
import sistema_maderera.entity.Usuario;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.AccesoUsuarioSedeRepository;
import sistema_maderera.repository.SedeRepository;
import sistema_maderera.repository.UsuarioRepository;
import sistema_maderera.repository.EmpresaRepository;
import sistema_maderera.security.UsuarioPrincipal;

@Service
@Transactional(readOnly = true)
public class AccesoSedeService {
    private final AccesoUsuarioSedeRepository accesos;
    private final SedeRepository sedes;
    private final UsuarioRepository usuarios;
    private final EmpresaRepository empresas;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    public AccesoSedeService(AccesoUsuarioSedeRepository accesos, SedeRepository sedes, UsuarioRepository usuarios,
            EmpresaRepository empresas) {
        this.accesos = accesos; this.sedes = sedes; this.usuarios = usuarios; this.empresas = empresas;
    }

    public List<PermisoSede> asignados(Usuario usuario) {
        var asignados = accesos.findByUsuarioIdOrderBySedeNombreAsc(usuario.getId());
        if (!asignados.isEmpty()) return asignados.stream()
                .filter(a -> a.getSede().getEmpresa().getId().equals(usuario.getEmpresa().getId()))
                .map(a -> new PermisoSede(a.getSede(), a.isPuedeGestionar())).toList();
        // Conserva la sede original de cuentas que aún no tienen asignaciones nuevas.
        return usuario.getSede() == null ? List.of()
                : List.of(new PermisoSede(usuario.getSede(), "ADMINISTRADOR".equals(usuario.getRol())));
    }

    public List<Sede> disponibles(Usuario usuario) {
        if (!habilitado(usuario)) return List.of();
        if ("ADMINISTRADOR_GENERAL".equals(usuario.getRol()))
            return sedes.findByEmpresaIdAndEstadoTrueOrderByNombreAsc(usuario.getEmpresa().getId());
        return asignados(usuario).stream().map(PermisoSede::sede)
                .filter(s -> Boolean.TRUE.equals(s.getEstado()) && s.getEmpresa().getId().equals(usuario.getEmpresa().getId()))
                .sorted(Comparator.comparing(Sede::getNombre, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public List<Sede> disponibles(UsuarioPrincipal principal) {
        return usuarios.findByIdAndEmpresaId(principal.getDatos().usuarioId(), principal.getDatos().empresaId())
                .map(this::disponibles).orElse(List.of());
    }

    public boolean tieneSedeActiva(Usuario usuario) {
        return habilitado(usuario) && tieneAsignacionActiva(usuario);
    }

    public boolean tieneAsignacionActiva(Usuario usuario) {
        return "ADMINISTRADOR_GENERAL".equals(usuario.getRol()) || asignados(usuario).stream()
                .anyMatch(a -> Boolean.TRUE.equals(a.sede().getEstado())
                        && a.sede().getEmpresa().getId().equals(usuario.getEmpresa().getId()));
    }

    public String descripcion(Usuario usuario) {
        if ("ADMINISTRADOR_GENERAL".equals(usuario.getRol())) return "Todas las sedes";
        return asignados(usuario).stream().map(a -> a.sede().getNombre()
                + (Boolean.TRUE.equals(a.sede().getEstado()) ? "" : " (inactiva)")
                + (a.gestion() ? " · Gestión" : " · Consulta"))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    public boolean puedeGestionar(UsuarioPrincipal principal, Long sedeId) {
        return usuarios.findByIdAndEmpresaId(principal.getDatos().usuarioId(), principal.getDatos().empresaId())
                .map(u -> puedeGestionar(u, sedeId)).orElse(false);
    }

    public boolean puedeGestionar(Usuario usuario, Long sedeId) {
        if (sedeId == null || !habilitado(usuario)) return false;
        if ("ADMINISTRADOR_GENERAL".equals(usuario.getRol()))
            return sedes.findByIdAndEmpresaIdAndEstadoTrue(sedeId, usuario.getEmpresa().getId()).isPresent();
        return asignados(usuario).stream().anyMatch(a -> a.gestion() && a.sede().getId().equals(sedeId)
                && Boolean.TRUE.equals(a.sede().getEstado()) && a.sede().getEmpresa().getId().equals(usuario.getEmpresa().getId()));
    }

    @Transactional
    public void exigirGestion(Usuario usuario, Long sedeId) {
        empresas.bloquearParaUsuarios(usuario.getEmpresa().getId()).orElseThrow();
        entityManager.refresh(usuario);
        entityManager.refresh(usuario.getEmpresa());
        boolean autorizado = false;
        if (habilitado(usuario) && sedeId != null) {
            if ("ADMINISTRADOR_GENERAL".equals(usuario.getRol())) autorizado = puedeGestionar(usuario, sedeId);
            else {
                // Lectura con bloqueo: evita usar permisos antiguos tras esperar otra modificación.
                var actuales = accesos.bloquearPermisos(usuario.getId());
                autorizado = actuales.isEmpty() ? puedeGestionar(usuario, sedeId) : actuales.stream()
                        .anyMatch(a -> a.isPuedeGestionar() && a.getSede().getId().equals(sedeId)
                                && Boolean.TRUE.equals(a.getSede().getEstado())
                                && a.getSede().getEmpresa().getId().equals(usuario.getEmpresa().getId()));
            }
        }
        if (!autorizado) throw new ReglaNegocioException("No tienes permiso para gestionar esta sede.");
    }

    private boolean habilitado(Usuario usuario) {
        return Boolean.TRUE.equals(usuario.getEstado()) && Boolean.TRUE.equals(usuario.getEmpresa().getEstado())
                && usuario.getEmpresa().getPlanComercial() != null
                && List.of("ADMINISTRADOR_GENERAL", "ADMINISTRADOR", "CONTADOR").contains(usuario.getRol());
    }

    public record PermisoSede(Sede sede, boolean gestion) {}
}
