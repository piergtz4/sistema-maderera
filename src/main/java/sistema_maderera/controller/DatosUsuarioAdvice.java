package sistema_maderera.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.EmpresaService;
import sistema_maderera.service.SedeContexto;

@ControllerAdvice
public class DatosUsuarioAdvice {
    private final Clock clock;
    private final EmpresaService empresas;
    private final SedeContexto sedes;

    public DatosUsuarioAdvice(Clock clock, EmpresaService empresas, SedeContexto sedes) {
        this.clock = clock;
        this.empresas = empresas;
        this.sedes = sedes;
    }

    @ModelAttribute
    public void cargarUsuario(Authentication authentication, HttpServletRequest request, Model model) {
        model.addAttribute("fechaActual", LocalDate.now(clock).format(
                DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-PE"))));
        if (authentication != null && authentication.getPrincipal() instanceof UsuarioPrincipal usuario) {
            var datos = usuario.getDatos();
            model.addAttribute("usuarioId", datos.usuarioId());
            model.addAttribute("usuarioNombre", datos.nombre());
            model.addAttribute("usuario", usuario.getUsername());
            model.addAttribute("rol", etiquetaRol(datos.rol()));
            boolean general = sedes.esAdministradorGeneral(usuario);
            boolean administradorSede = "ADMINISTRADOR".equals(datos.rol());
            model.addAttribute("esAdministradorGeneral", general);
            model.addAttribute("esAdministrador", general || administradorSede);
            model.addAttribute("puedeEditarCatalogo", general);
            model.addAttribute("puedeOperarInventario", general || administradorSede);
            model.addAttribute("empresaNombre", empresas.nombre(datos.empresaId()));
            model.addAttribute("empresaTieneLogo", empresas.tieneLogo(datos.empresaId()));
            var sede = sedes.actual(usuario, request.getSession(false)).orElse(null);
            model.addAttribute("sedeId", sede == null ? null : sede.getId());
            model.addAttribute("sedeNombre", sede == null ? "Sin sede seleccionada" : sede.getNombre());
            model.addAttribute("sedeDepartamento", sede == null ? "" : sede.getDepartamento());
            model.addAttribute("sedeProvincia", sede == null ? "" : sede.getProvincia());
            model.addAttribute("sedeDistrito", sede == null ? "" : sede.getDistrito());
        }
    }

    private String etiquetaRol(String rol) {
        return switch (rol) {
            case "ADMINISTRADOR_GENERAL" -> "Administrador general";
            case "ADMINISTRADOR" -> "Administrador de sede";
            case "CONSULTA" -> "Consulta";
            default -> rol;
        };
    }
}
