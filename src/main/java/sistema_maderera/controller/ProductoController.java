package sistema_maderera.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.dto.ProductoForm;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.CategoriaService;
import sistema_maderera.service.InventarioService;
import sistema_maderera.service.ProductoService;
import sistema_maderera.service.RegistroProductoService;
import sistema_maderera.service.SedeContexto;

@Controller
public class ProductoController {
    private final ProductoService productos;
    private final CategoriaService categorias;
    private final InventarioService inventario;
    private final RegistroProductoService registro;
    private final SedeContexto contexto;

    public ProductoController(ProductoService productos, CategoriaService categorias, InventarioService inventario,
            RegistroProductoService registro, SedeContexto contexto) {
        this.productos = productos;
        this.categorias = categorias;
        this.inventario = inventario;
        this.registro = registro;
        this.contexto = contexto;
    }

    @GetMapping("/productos")
    public String listar(Model model) {
        model.addAttribute("productos", productos.listarTodos());
        model.addAttribute("sedesPorProducto", inventario.sedesRegistradasPorProducto());
        return "productos";
    }

    @GetMapping("/productos/nuevo")
    public String nuevo(@AuthenticationPrincipal UsuarioPrincipal usuario, HttpSession sesion, Model model) {
        if (contexto.actual(usuario, sesion).isEmpty()) return "redirect:/seleccionar-sede";
        ProductoForm form = new ProductoForm();
        form.setStockInicial(BigDecimal.ZERO);
        form.setStockMinimo(BigDecimal.ZERO);
        model.addAttribute("producto", form);
        cargarCategorias(model, form);
        return "producto-form";
    }

    @GetMapping("/productos/editar/{id}")
    public String editar(@PathVariable Long id, @AuthenticationPrincipal UsuarioPrincipal usuario,
            HttpSession sesion, Model model) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        ProductoForm form = registro.formularioEnSede(sede.get().getId(), id);
        model.addAttribute("producto", form);
        model.addAttribute("inventarioRegistrado", form.getInventarioVersion() != null);
        cargarCategorias(model, form);
        return "producto-form";
    }

    @PostMapping("/productos/guardar")
    public String guardar(@AuthenticationPrincipal UsuarioPrincipal usuario,
            @Valid @ModelAttribute("producto") ProductoForm form, BindingResult errores,
            HttpSession sesion, Model model, RedirectAttributes flash) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        Long sedeId = sede.get().getId();
        if (!errores.hasErrors()) {
            try {
                if (form.getId() == null) {
                    registro.crearEnSede(sedeId, usuario.getDatos().usuarioId(), form);
                    flash.addFlashAttribute("mensaje", "Producto creado con su inventario en esta sede.");
                } else {
                    registro.actualizarEnSede(sedeId, usuario.getDatos().usuarioId(), form);
                    flash.addFlashAttribute("mensaje", "Producto guardado correctamente.");
                }
                return "redirect:/productos";
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                errores.reject("duplicado", "No se pudo guardar. Revise los datos e intente nuevamente.");
            }
        }
        model.addAttribute("inventarioRegistrado", form.getInventarioVersion() != null);
        cargarCategorias(model, form);
        return "producto-form";
    }

    @PostMapping("/productos/{id}/inventario")
    public String guardarInventario(@PathVariable Long id, @AuthenticationPrincipal UsuarioPrincipal usuario,
            @ModelAttribute("producto") ProductoForm form, BindingResult errores,
            HttpSession sesion, RedirectAttributes flash) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        if (!errores.hasErrors()) {
            try {
                registro.actualizarSoloInventario(sede.get().getId(), usuario.getDatos().usuarioId(), id, form);
                flash.addFlashAttribute("mensaje", "Inventario de esta sede actualizado.");
                return "redirect:/productos";
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                errores.reject("duplicado", "El inventario cambió. Vuelve a abrir el producto.");
            }
        }
        flash.addFlashAttribute("error", errores.getAllErrors().getFirst().getDefaultMessage());
        return "redirect:/productos/editar/" + id;
    }

    @PostMapping("/productos/cambiar-estado/{id}")
    public String cambiarEstado(@PathVariable Long id, RedirectAttributes flash) {
        productos.cambiarEstado(id);
        flash.addFlashAttribute("mensaje", "Estado del producto actualizado.");
        return "redirect:/productos";
    }

    private void cargarCategorias(Model model, ProductoForm form) {
        model.addAttribute("categorias", categorias.listarTodas().stream()
                .filter(c -> c.getEstado() || c.getId().equals(form.getCategoriaId())).toList());
    }

}
