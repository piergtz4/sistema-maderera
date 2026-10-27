package sistema_maderera.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.dto.UsuarioForm;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.SedeContexto;
import sistema_maderera.service.UsuarioGestionService;
import sistema_maderera.service.UsuarioService;

@Controller
@RequestMapping("/usuarios")
public class UsuarioController {
    private final UsuarioGestionService usuarios;
    private final UsuarioService sesiones;
    private final SedeContexto sedes;

    public UsuarioController(UsuarioGestionService usuarios, UsuarioService sesiones, SedeContexto sedes) {
        this.usuarios = usuarios;
        this.sesiones = sesiones;
        this.sedes = sedes;
    }

    @GetMapping
    public String listar(@AuthenticationPrincipal UsuarioPrincipal actor, Model model) {
        model.addAttribute("cuentas", usuarios.listar(actor.getDatos().empresaId()));
        return "usuarios";
    }

    @GetMapping("/nuevo")
    public String nuevo(@AuthenticationPrincipal UsuarioPrincipal actor, Model model) {
        model.addAttribute("cuenta", new UsuarioForm());
        model.addAttribute("sedes", sedes.disponibles(actor));
        return "usuario-form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@AuthenticationPrincipal UsuarioPrincipal actor, @PathVariable Long id, Model model) {
        model.addAttribute("cuenta", usuarios.formulario(actor.getDatos().empresaId(), id));
        model.addAttribute("sedes", sedes.disponibles(actor));
        return "usuario-form";
    }

    @PostMapping("/guardar")
    public String guardar(@AuthenticationPrincipal UsuarioPrincipal actor,
            @Valid @ModelAttribute("cuenta") UsuarioForm form, BindingResult errores,
            HttpSession sesion, Model model, RedirectAttributes flash) {
        if (!errores.hasErrors()) {
            try {
                var guardado = usuarios.guardar(actor.getDatos().empresaId(), actor.getDatos().usuarioId(), form);
                if (guardado.getId().equals(actor.getDatos().usuarioId()) && !sesiones.sesionVigente(actor)) {
                    sesion.invalidate();
                    SecurityContextHolder.clearContext();
                    return "redirect:/?cuenta";
                }
                flash.addFlashAttribute("mensaje", "Usuario guardado correctamente.");
                return "redirect:/usuarios";
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                errores.reject("duplicado", "Ese nombre de usuario ya está registrado.");
            }
        }
        model.addAttribute("sedes", sedes.disponibles(actor));
        return "usuario-form";
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstado(@AuthenticationPrincipal UsuarioPrincipal actor, @PathVariable Long id,
            RedirectAttributes flash) {
        try {
            boolean activo = usuarios.cambiarEstado(actor.getDatos().empresaId(), actor.getDatos().usuarioId(), id);
            flash.addFlashAttribute("mensaje", activo ? "Usuario activado." : "Usuario desactivado.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/usuarios";
    }
}
