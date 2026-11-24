package sistema_maderera.controller;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
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
                    : "Sede desactivada. Las cuentas sin otra sede activa quedaron inactivas. Su historial se conserva.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/sedes";
    }

    @GetMapping("/{id}/eliminar")
    public String confirmarEliminacion(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            Model model, RedirectAttributes flash) {
        try {
            model.addAttribute("nombreRegistro", sedes.nombreParaEliminar(usuario.getDatos().empresaId(), id));
            model.addAttribute("tipoRegistro", "sede");
            model.addAttribute("accionEliminar", "/sedes/" + id + "/eliminar");
            model.addAttribute("volver", "/sedes");
            model.addAttribute("efectoEliminacion", "La sede se borrará definitivamente. Solo puedes eliminarla si no tiene usuarios asignados, inventario, compras, ventas ni numeración asociados.");
            return "eliminar-registro";
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/sedes";
        }
    }

    @PostMapping("/{id}/eliminar")
    public String eliminar(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean confirmar, RedirectAttributes flash) {
        try {
            sedes.eliminar(usuario.getDatos().empresaId(), usuario.getDatos().usuarioId(), id, confirmar);
            flash.addFlashAttribute("mensaje", "Sede eliminada correctamente.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        } catch (DataIntegrityViolationException e) {
            flash.addFlashAttribute("error", "No se pudo eliminar la sede porque tiene registros asociados. Actualiza la lista para revisarla.");
        }
        return "redirect:/sedes";
    }
}
