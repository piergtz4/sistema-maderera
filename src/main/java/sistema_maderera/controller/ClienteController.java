package sistema_maderera.controller;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.dto.ClienteForm;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.ClienteService;

@Controller
@RequestMapping("/clientes")
public class ClienteController {
    private final ClienteService clientes;

    public ClienteController(ClienteService clientes) { this.clientes = clientes; }

    @PostMapping("/crear-desde-venta")
    @ResponseBody
    public ResponseEntity<?> crearDesdeVenta(@AuthenticationPrincipal UsuarioPrincipal usuario,
            @Valid @ModelAttribute ClienteForm form, BindingResult errores) {
        if (form.getId() != null || form.getVersion() != null) errores.reject("nuevo", "Esta opción solo registra clientes nuevos.");
        if (errores.hasErrors()) return ResponseEntity.badRequest().body(new ErroresCliente(
                errores.getAllErrors().stream().map(e -> e.getDefaultMessage()).toList()));
        try {
            var cliente = clientes.guardar(usuario.getDatos().empresaId(), form);
            return ResponseEntity.ok(new ClienteCreado(cliente.getId(), cliente.getNombre(),
                    cliente.getNumeroDocumento(), cliente.getTipoIdentificacion().name(), cliente.getDireccion()));
        } catch (ReglaNegocioException e) {
            return ResponseEntity.badRequest().body(new ErroresCliente(List.of(e.getMessage())));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.status(409).body(new ErroresCliente(List.of("Ya existe un cliente con ese documento.")));
        }
    }

    public record ClienteCreado(Long id, String nombre, String numeroDocumento, String tipoIdentificacion, String direccion) { }
    public record ErroresCliente(List<String> errores) { }

    @GetMapping
    public String listar(@AuthenticationPrincipal UsuarioPrincipal usuario, Model model) {
        model.addAttribute("clientes", clientes.listar(usuario.getDatos().empresaId()));
        return "clientes";
    }

    @GetMapping("/nuevo")
    public String nuevo(Model model) {
        model.addAttribute("cliente", new ClienteForm());
        return "cliente-form";
    }

    @GetMapping("/{id}/editar")
    public String editar(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id, Model model) {
        model.addAttribute("cliente", clientes.formulario(usuario.getDatos().empresaId(), id));
        return "cliente-form";
    }

    @PostMapping("/guardar")
    public String guardar(@AuthenticationPrincipal UsuarioPrincipal usuario,
            @Valid @ModelAttribute("cliente") ClienteForm form, BindingResult errores, RedirectAttributes flash) {
        if (!errores.hasErrors()) {
            try {
                clientes.guardar(usuario.getDatos().empresaId(), form);
                flash.addFlashAttribute("mensaje", "Cliente guardado correctamente.");
                return "redirect:/clientes";
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                errores.reject("duplicado", "Ya existe un cliente con ese documento.");
            } catch (OptimisticLockingFailureException e) {
                errores.reject("concurrencia", "El cliente cambió. Vuelve a abrirlo antes de guardar.");
            }
        }
        return "cliente-form";
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstado(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            RedirectAttributes flash) {
        clientes.cambiarEstado(usuario.getDatos().empresaId(), id);
        flash.addFlashAttribute("mensaje", "Estado del cliente actualizado.");
        return "redirect:/clientes";
    }
}
