package sistema_maderera.controller;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.dto.ProveedorForm;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.ProveedorService;

@Controller
@RequestMapping("/proveedores")
public class ProveedorController {
    private final ProveedorService proveedores;

    public ProveedorController(ProveedorService proveedores) { this.proveedores = proveedores; }

    @GetMapping
    public String listar(@AuthenticationPrincipal UsuarioPrincipal usuario, Model model) {
        model.addAttribute("proveedores", proveedores.listar(usuario.getDatos().empresaId()));
        return "proveedores";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("proveedor", new ProveedorForm());
        return "proveedor-form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id, Model model) {
        model.addAttribute("proveedor", proveedores.formulario(usuario.getDatos().empresaId(), id));
        return "proveedor-form";
    }

    @PostMapping("/guardar")
    public String guardar(@AuthenticationPrincipal UsuarioPrincipal usuario,
            @Valid @ModelAttribute("proveedor") ProveedorForm form, BindingResult errores, RedirectAttributes flash) {
        if (!errores.hasErrors()) {
            try {
                proveedores.guardar(usuario.getDatos().empresaId(), form);
                flash.addFlashAttribute("mensaje", "Proveedor guardado correctamente.");
                return "redirect:/proveedores";
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                errores.reject("duplicado", "Ya existe un proveedor con ese documento.");
            } catch (OptimisticLockingFailureException e) {
                errores.reject("concurrencia", "El proveedor cambió. Vuelve a abrirlo antes de guardar.");
            }
        }
        return "proveedor-form";
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstado(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            RedirectAttributes flash) {
        proveedores.cambiarEstado(usuario.getDatos().empresaId(), id);
        flash.addFlashAttribute("mensaje", "Estado del proveedor actualizado.");
        return "redirect:/proveedores";
    }
}
