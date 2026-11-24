package sistema_maderera.service;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import sistema_maderera.entity.Sede;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.UsuarioPrincipal;

@Service
public class SedeContexto {
    private static final String SEDE_TRABAJO = "sedeTrabajoId";
    private final AccesoSedeService accesos;

    public SedeContexto(AccesoSedeService accesos) { this.accesos = accesos; }

    public boolean esAdministradorGeneral(UsuarioPrincipal usuario) {
        return "ADMINISTRADOR_GENERAL".equals(usuario.getDatos().rol());
    }

    public List<Sede> disponibles(UsuarioPrincipal usuario) {
        return accesos.disponibles(usuario);
    }

    public Optional<Sede> actual(UsuarioPrincipal usuario, HttpSession sesion) {
        List<Sede> disponibles = disponibles(usuario);
        if (disponibles.size() == 1) {
            if (sesion != null) sesion.removeAttribute(SEDE_TRABAJO);
            return Optional.of(disponibles.getFirst());
        }
        Object seleccion = sesion == null ? null : sesion.getAttribute(SEDE_TRABAJO);
        Optional<Sede> sede = disponibles.stream().filter(s -> s.getId().equals(seleccion)).findFirst();
        if (sede.isEmpty() && sesion != null) {
            sesion.removeAttribute(SEDE_TRABAJO);
        }
        return sede;
    }

    public void seleccionar(UsuarioPrincipal usuario, HttpSession sesion, Long sedeId) {
        Sede sede = disponibles(usuario).stream().filter(s -> s.getId().equals(sedeId)).findFirst()
                .orElseThrow(() -> new ReglaNegocioException("Selecciona una sede activa que tengas autorizada."));
        sesion.setAttribute(SEDE_TRABAJO, sede.getId());
    }
}
