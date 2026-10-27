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

    public UsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return usuarioRepository.findByUsuarioIgnoreCase(username.trim()).map(UsuarioPrincipal::new)
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
                && ("ADMINISTRADOR_GENERAL".equals(usuario.getRol()) || usuario.getSede() != null
                    && Boolean.TRUE.equals(usuario.getSede().getEstado()))
                && Objects.equals(usuario.getUsuario(), principal.getUsername())
                && Objects.equals(usuario.getNombre(), datos.nombre())
                && Objects.equals(usuario.getRol(), datos.rol())
                && Objects.equals(usuario.getEmpresa().getId(), datos.empresaId())
                && Objects.equals(sedeId, datos.sedeId())
                && Objects.equals(UsuarioPrincipal.huellaClave(usuario.getPassword()), datos.huellaClave());
    }
}
