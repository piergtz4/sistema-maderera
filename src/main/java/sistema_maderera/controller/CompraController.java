package sistema_maderera.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.dto.*;
import sistema_maderera.entity.TipoComprobanteCompra;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.*;

@Controller
@RequestMapping("/compras")
public class CompraController {
    private final CompraService compras;
    private final ProveedorService proveedores;
    private final SedeContexto contexto;
    public CompraController(CompraService compras, ProveedorService proveedores, SedeContexto contexto) {
        this.compras = compras;
        this.proveedores = proveedores;
        this.contexto = contexto;
    }

    @GetMapping
    public String listar(@AuthenticationPrincipal UsuarioPrincipal usuario, HttpSession sesion, Model model) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        model.addAttribute("compras", compras.listar(sede.get().getId()));
        return "compras";
    }

    @GetMapping("/nueva")
    public String nueva(@AuthenticationPrincipal UsuarioPrincipal usuario, HttpSession sesion, Model model) {
        if (contexto.actual(usuario, sesion).isEmpty()) return "redirect:/seleccionar-sede";
        CompraForm form = new CompraForm();
        form.setFecha(compras.hoy());
        form.getLineas().add(new DetalleCompraForm());
        model.addAttribute("compra", form);
        return cargar(usuario, model);
    }

    @GetMapping("/{id}/editar")
    public String editar(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            HttpSession sesion, Model model, RedirectAttributes flash) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        try {
            model.addAttribute("compra", compras.formulario(sede.get().getId(), id));
            return cargar(usuario, model);
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/compras/" + id;
        }
    }

    @PostMapping("/guardar")
    public String guardar(@AuthenticationPrincipal UsuarioPrincipal usuario, @Valid @ModelAttribute("compra") CompraForm form,
            BindingResult errores, HttpSession sesion, Model model, RedirectAttributes flash) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        if (!errores.hasErrors()) {
            try {
                var compra = compras.guardar(sede.get().getId(), usuario.getDatos().usuarioId(), form);
                flash.addFlashAttribute("mensaje", "Compra pendiente guardada. Revisa los datos antes de confirmar el ingreso.");
                return "redirect:/compras/" + compra.getId();
            } catch (ReglaNegocioException e) { errores.reject("negocio", e.getMessage()); }
            catch (DataIntegrityViolationException e) { errores.reject("duplicado", "El comprobante ya está registrado. Revisa proveedor, tipo y referencia."); }
            catch (OptimisticLockingFailureException e) { errores.reject("concurrencia", "La compra cambió. Vuelve a abrirla."); }
        }
        return cargar(usuario, model);
    }

    @GetMapping("/{id}")
    public String ver(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id, HttpSession sesion, Model model) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        model.addAttribute("compra", compras.ver(sede.get().getId(), id));
        return "compra-detalle";
    }

    @PostMapping("/{id}/confirmar")
    public String confirmar(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            @RequestParam Long version, HttpSession sesion, RedirectAttributes flash) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        try {
            compras.confirmar(sede.get().getId(), usuario.getDatos().usuarioId(), id, version);
            flash.addFlashAttribute("mensaje", "Compra confirmada. Mercadería ingresada al inventario de esta sede.");
        } catch (ReglaNegocioException e) { flash.addFlashAttribute("error", e.getMessage()); }
        catch (OptimisticLockingFailureException | DataIntegrityViolationException e) {
            flash.addFlashAttribute("error", "El registro cambió mientras confirmabas. Revisa la compra antes de reintentar.");
        }
        return "redirect:/compras/" + id;
    }

    @PostMapping("/{id}/descartar")
    public String descartar(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            @RequestParam Long version, HttpSession sesion, RedirectAttributes flash) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        try {
            compras.descartar(sede.get().getId(), usuario.getDatos().usuarioId(), id, version);
            flash.addFlashAttribute("mensaje", "Compra pendiente eliminada sin modificar el stock.");
        } catch (ReglaNegocioException e) { flash.addFlashAttribute("error", e.getMessage()); }
        catch (OptimisticLockingFailureException e) { flash.addFlashAttribute("error", "La compra cambió. Vuelve a abrirla."); }
        return "redirect:/compras";
    }

    private String cargar(UsuarioPrincipal usuario, Model model) {
        if (model.getAttribute("compra") instanceof CompraForm form && form.getLineas() != null && form.getLineas().isEmpty()) {
            form.getLineas().add(new DetalleCompraForm());
        }
        model.addAttribute("proveedores", proveedores.listar(usuario.getDatos().empresaId()));
        model.addAttribute("productos", compras.productosDisponibles());
        model.addAttribute("tipos", TipoComprobanteCompra.values());
        model.addAttribute("hoy", compras.hoy());
        return "compra-form";
    }
}
