package sistema_maderera.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.dto.SedeForm;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.SedeService;

@Controller
@RequestMapping("/sedes")
public class SedeController {
    private final SedeService sedes;

    public SedeController(SedeService sedes) { this.sedes = sedes; }

    @GetMapping
    public String listar(@AuthenticationPrincipal UsuarioPrincipal usuario, Model model) {
        model.addAttribute("sedes", sedes.listar(usuario.getDatos().empresaId()));
        return "sedes";
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        model.addAttribute("sede", new SedeForm());
        return "sede-form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id, Model model) {
        model.addAttribute("sede", sedes.formulario(usuario.getDatos().empresaId(), id));
        return "sede-form";
    }

    @PostMapping("/guardar")
    public String guardar(@AuthenticationPrincipal UsuarioPrincipal usuario,
            @Valid @ModelAttribute("sede") SedeForm form, BindingResult errores, RedirectAttributes flash) {
        if (!errores.hasErrors()) {
            try {
                sedes.guardar(usuario.getDatos().empresaId(), form);
                flash.addFlashAttribute("mensaje", "Sede guardada correctamente.");
                return "redirect:/sedes";
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            }
        }
        return "sede-form";
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstado(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            RedirectAttributes flash) {
        try {
            boolean activa = sedes.cambiarEstado(usuario.getDatos().empresaId(), id);
            flash.addFlashAttribute("mensaje", activa ? "Sede activada. Reactiva sus usuarios desde Usuarios si corresponde."
                    : "Sede y usuarios asignados desactivados. Su historial se conserva.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/sedes";
    }
}
