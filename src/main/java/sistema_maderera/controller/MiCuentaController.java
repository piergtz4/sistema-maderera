package sistema_maderera.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import sistema_maderera.dto.CambioClaveForm;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.UsuarioGestionService;

@Controller
public class MiCuentaController {
    private final UsuarioGestionService usuarios;

    public MiCuentaController(UsuarioGestionService usuarios) { this.usuarios = usuarios; }

    @GetMapping("/mi-cuenta")
    public String mostrar(Model model) {
        model.addAttribute("clave", new CambioClaveForm());
        return "mi-cuenta";
    }

    @PostMapping("/mi-cuenta/clave")
    public String cambiarClave(@AuthenticationPrincipal UsuarioPrincipal actor,
            @Valid @ModelAttribute("clave") CambioClaveForm form, BindingResult errores,
            HttpSession sesion) {
        if (!errores.hasErrors()) {
            try {
                usuarios.cambiarClave(actor.getDatos().usuarioId(), form);
                sesion.invalidate();
                SecurityContextHolder.clearContext();
                return "redirect:/?clave";
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            }
        }
        return "mi-cuenta";
    }
}
