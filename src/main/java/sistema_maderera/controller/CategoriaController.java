package sistema_maderera.controller;

import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.dto.CategoriaForm;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.service.CategoriaService;
import sistema_maderera.security.UsuarioPrincipal;

@Controller
public class CategoriaController {
    private final CategoriaService categorias;

    public CategoriaController(CategoriaService categorias) { this.categorias = categorias; }

    @GetMapping("/categorias")
    public String listar(@AuthenticationPrincipal UsuarioPrincipal usuario, Model model) {
        model.addAttribute("categorias", categorias.listarTodas(usuario.getDatos().empresaId()));
        return "categorias";
    }

    @GetMapping("/categorias/nueva")
    public String nueva(@AuthenticationPrincipal UsuarioPrincipal usuario, Model model) {
        model.addAttribute("categoria", new CategoriaForm());
        cargarCodigos(usuario.getDatos().empresaId(), model);
        return "categoria-form";
    }

    @GetMapping("/categorias/editar/{id}")
    public String editar(@PathVariable Long id, @AuthenticationPrincipal UsuarioPrincipal usuario, Model model) {
        model.addAttribute("categoria", categorias.formulario(usuario.getDatos().empresaId(), id));
        cargarCodigos(usuario.getDatos().empresaId(), model);
        return "categoria-form";
    }

    @PostMapping("/categorias/guardar")
    public String guardar(@AuthenticationPrincipal UsuarioPrincipal usuario,
            @Valid @ModelAttribute("categoria") CategoriaForm form, BindingResult errores,
            Model model, RedirectAttributes flash) {
        if (!errores.hasErrors()) {
            try {
                categorias.guardar(usuario.getDatos().empresaId(), form);
                flash.addFlashAttribute("mensaje", "Categoría guardada correctamente.");
                return "redirect:/categorias";
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                errores.reject("duplicado", "El código o nombre ya está registrado.");
            }
        }
        cargarCodigos(usuario.getDatos().empresaId(), model);
        return "categoria-form";
    }

    @PostMapping("/categorias/cambiar-estado/{id}")
    public String cambiarEstado(@PathVariable Long id, @AuthenticationPrincipal UsuarioPrincipal usuario,
            RedirectAttributes flash) {
        categorias.cambiarEstado(usuario.getDatos().empresaId(), id);
        flash.addFlashAttribute("mensaje", "Estado de la categoría actualizado.");
        return "redirect:/categorias";
    }

    private void cargarCodigos(Long empresaId, Model model) {
        model.addAttribute("codigosCategorias", String.join(",", categorias.listarTodas(empresaId).stream()
                .map(c -> c.getCodigo().toUpperCase(Locale.ROOT)).toList()));
    }
}
