package sistema_maderera.service;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import sistema_maderera.entity.Sede;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.SedeRepository;
import sistema_maderera.security.UsuarioPrincipal;

@Service
public class SedeContexto {
    private static final String SEDE_TRABAJO = "sedeTrabajoId";
    private final SedeRepository sedes;

    public SedeContexto(SedeRepository sedes) { this.sedes = sedes; }

    public boolean esAdministradorGeneral(UsuarioPrincipal usuario) {
        return "ADMINISTRADOR_GENERAL".equals(usuario.getDatos().rol());
    }

    public List<Sede> disponibles(UsuarioPrincipal usuario) {
        return sedes.findByEmpresaIdAndEstadoTrueOrderByNombreAsc(usuario.getDatos().empresaId());
    }

    public Optional<Sede> actual(UsuarioPrincipal usuario, HttpSession sesion) {
        Long sedeId = usuario.getDatos().sedeId();
        if (esAdministradorGeneral(usuario)) {
            Object valor = sesion == null ? null : sesion.getAttribute(SEDE_TRABAJO);
            sedeId = valor instanceof Long id ? id : null;
        }
        if (sedeId == null) return Optional.empty();
        Optional<Sede> sede = sedes.findByIdAndEmpresaIdAndEstadoTrue(sedeId, usuario.getDatos().empresaId());
        if (sede.isEmpty() && esAdministradorGeneral(usuario) && sesion != null) {
            sesion.removeAttribute(SEDE_TRABAJO);
        }
        return sede;
    }

    public void seleccionar(UsuarioPrincipal usuario, HttpSession sesion, Long sedeId) {
        if (!esAdministradorGeneral(usuario)) {
            throw new ReglaNegocioException("Tu usuario tiene una sede asignada.");
        }
        Sede sede = sedes.findByIdAndEmpresaIdAndEstadoTrue(sedeId, usuario.getDatos().empresaId())
                .orElseThrow(() -> new ReglaNegocioException("Selecciona una sede activa de tu empresa."));
        sesion.setAttribute(SEDE_TRABAJO, sede.getId());
    }
}
