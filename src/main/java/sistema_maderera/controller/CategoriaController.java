package sistema_maderera.controller;

import jakarta.validation.Valid;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.dto.CategoriaForm;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.service.CategoriaService;

@Controller
public class CategoriaController {
    private final CategoriaService categorias;

    public CategoriaController(CategoriaService categorias) { this.categorias = categorias; }

    @GetMapping("/categorias")
    public String listar(Model model) {
        model.addAttribute("categorias", categorias.listarTodas());
        return "categorias";
    }

    @GetMapping("/categorias/nueva")
    public String nueva(Model model) {
        model.addAttribute("categoria", new CategoriaForm());
        cargarCodigos(model);
        return "categoria-form";
    }

    @GetMapping("/categorias/editar/{id}")
    public String editar(@PathVariable Long id, Model model) {
        model.addAttribute("categoria", categorias.formulario(id));
        cargarCodigos(model);
        return "categoria-form";
    }

    @PostMapping("/categorias/guardar")
    public String guardar(@Valid @ModelAttribute("categoria") CategoriaForm form, BindingResult errores,
            Model model, RedirectAttributes flash) {
        if (!errores.hasErrors()) {
            try {
                categorias.guardar(form);
                flash.addFlashAttribute("mensaje", "Categoría guardada correctamente.");
                return "redirect:/categorias";
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                errores.reject("duplicado", "El código o nombre ya está registrado.");
            }
        }
        cargarCodigos(model);
        return "categoria-form";
    }

    @PostMapping("/categorias/cambiar-estado/{id}")
    public String cambiarEstado(@PathVariable Long id, RedirectAttributes flash) {
        categorias.cambiarEstado(id);
        flash.addFlashAttribute("mensaje", "Estado de la categoría actualizado.");
        return "redirect:/categorias";
    }

    private void cargarCodigos(Model model) {
        model.addAttribute("codigosCategorias", String.join(",", categorias.listarTodas().stream()
                .map(c -> c.getCodigo().toUpperCase(Locale.ROOT)).toList()));
    }
}
