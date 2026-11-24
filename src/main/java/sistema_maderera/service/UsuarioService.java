package sistema_maderera.service;

import java.util.Objects;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.repository.UsuarioRepository;
import sistema_maderera.entity.Usuario;
import sistema_maderera.security.UsuarioPrincipal;

@Service
public class UsuarioService implements UserDetailsService {
    private final UsuarioRepository usuarioRepository;
    private final AccesoSedeService accesos;

    public UsuarioService(UsuarioRepository usuarioRepository, AccesoSedeService accesos) {
        this.usuarioRepository = usuarioRepository;
        this.accesos = accesos;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return usuarioRepository.findByUsuarioIgnoreCase(username.trim())
                .map(u -> new UsuarioPrincipal(u, accesos.tieneSedeActiva(u)))
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales incorrectas."));
    }

    @Transactional(readOnly = true)
    public boolean sesionVigente(UsuarioPrincipal principal) {
        return usuarioRepository.buscarSesion(principal.getDatos().usuarioId())
                .filter(usuario -> coincide(principal, usuario)).isPresent();
    }

    private boolean coincide(UsuarioPrincipal principal, Usuario usuario) {
        var datos = principal.getDatos();
        Long sedeId = usuario.getSede() == null ? null : usuario.getSede().getId();
        return Boolean.TRUE.equals(usuario.getEstado())
                && Boolean.TRUE.equals(usuario.getEmpresa().getEstado())
                && accesos.tieneSedeActiva(usuario)
                && Objects.equals(usuario.getUsuario(), principal.getUsername())
                && Objects.equals(usuario.getNombre(), datos.nombre())
                && Objects.equals(usuario.getRol(), datos.rol())
                && Objects.equals(usuario.getEmpresa().getId(), datos.empresaId())
                && Objects.equals(usuario.getEmpresa().getVersionAcceso(), datos.versionAccesoEmpresa())
                && Objects.equals(usuario.getVersionAcceso(), datos.versionAccesoUsuario())
                && Objects.equals(sedeId, datos.sedeId())
                && Objects.equals(UsuarioPrincipal.huellaClave(usuario.getPassword()), datos.huellaClave());
    }
}
