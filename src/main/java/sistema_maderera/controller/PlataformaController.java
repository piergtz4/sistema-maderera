package sistema_maderera.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import sistema_maderera.security.AdministradorPlataformaPrincipal;

import sistema_maderera.service.PlataformaEmpresaService;

@Controller
@RequestMapping("/plataforma")
public class PlataformaController {

    private final PlataformaEmpresaService empresas;

    public PlataformaController(PlataformaEmpresaService empresas) {
        this.empresas = empresas;
    }

    @GetMapping("/login")
    public String login(
            @AuthenticationPrincipal AdministradorPlataformaPrincipal administrador) {

        if (administrador != null) {
            return "redirect:/plataforma";
        }

        return "plataforma/login";
    }

    @GetMapping({"", "/"})
    public String inicio(
            @AuthenticationPrincipal AdministradorPlataformaPrincipal administrador,
            @RequestParam(defaultValue = "activas") String estado,
            Model model) {

        Boolean activas = switch (estado) {
            case "activas" -> true;
            case "suspendidas" -> false;
            case "todas" -> null;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona un estado válido.");
        };
        model.addAttribute("nombreAdministrador", administrador.getNombre());
        model.addAttribute("empresas", empresas.listar(activas));
        model.addAttribute("estado", estado);
        return "plataforma/inicio";
    }
}
