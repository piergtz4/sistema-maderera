package sistema_maderera.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.SedeContexto;

@Controller
public class SedeTrabajoController {
    private final SedeContexto contexto;

    public SedeTrabajoController(SedeContexto contexto) { this.contexto = contexto; }

    @GetMapping("/seleccionar-sede")
    public String seleccionar(@AuthenticationPrincipal UsuarioPrincipal usuario, Model model) {
        model.addAttribute("sedes", contexto.disponibles(usuario));
        return "seleccionar-sede";
    }

    @PostMapping("/sede-trabajo")
    public String cambiar(@AuthenticationPrincipal UsuarioPrincipal usuario, HttpSession sesion,
            @RequestParam(required = false) Long sedeId, RedirectAttributes flash) {
        try {
            contexto.seleccionar(usuario, sesion, sedeId);
            flash.addFlashAttribute("mensaje", "Sede de trabajo actualizada.");
            return "redirect:/dashboard";
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/seleccionar-sede";
        }
    }
}
