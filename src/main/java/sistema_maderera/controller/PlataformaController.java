package sistema_maderera.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
            Model model) {

        model.addAttribute("nombreAdministrador", administrador.getNombre());
        model.addAttribute("empresas", empresas.listar());
        return "plataforma/inicio";
    }
}