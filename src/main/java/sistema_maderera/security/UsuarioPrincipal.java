package sistema_maderera.security;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import sistema_maderera.entity.Sede;
import sistema_maderera.entity.Usuario;

public class UsuarioPrincipal extends User {
    private final DatosUsuario datos;

    public UsuarioPrincipal(Usuario usuario) {
        super(usuario.getUsuario(), usuario.getPassword(),
                Boolean.TRUE.equals(usuario.getEstado())
                        && Boolean.TRUE.equals(usuario.getEmpresa().getEstado())
                        && ("ADMINISTRADOR_GENERAL".equals(usuario.getRol())
                            || usuario.getSede() != null && Boolean.TRUE.equals(usuario.getSede().getEstado())),
                true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol())));
        Sede sede = usuario.getSede();
        datos = new DatosUsuario(usuario.getId(), usuario.getNombre(), usuario.getRol(),
                usuario.getEmpresa().getId(), sede == null ? null : sede.getId(),
                huellaClave(usuario.getPassword()));
    }

    public DatosUsuario getDatos() {
        return datos;
    }

    public static String huellaClave(String hash) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(hash.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible.", e);
        }
    }

    public record DatosUsuario(Long usuarioId, String nombre, String rol, Long empresaId, Long sedeId,
            String huellaClave)
            implements Serializable {
    }
}
