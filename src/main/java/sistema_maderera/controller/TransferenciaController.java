package sistema_maderera.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.dto.TransferenciaForm;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.InventarioService;
import sistema_maderera.service.SedeContexto;
import sistema_maderera.service.TransferenciaService;

@Controller
@RequestMapping("/transferencias")
public class TransferenciaController {
    private final TransferenciaService transferencias;
    private final InventarioService inventario;
    private final SedeContexto contexto;

    public TransferenciaController(TransferenciaService transferencias, InventarioService inventario,
            SedeContexto contexto) {
        this.transferencias = transferencias;
        this.inventario = inventario;
        this.contexto = contexto;
    }

    @GetMapping
    public String listar(@AuthenticationPrincipal UsuarioPrincipal usuario, HttpSession sesion, Model model) {
        return cargar(usuario, sesion, model);
    }

    @PostMapping
    public String registrar(@AuthenticationPrincipal UsuarioPrincipal usuario,
            @Valid @ModelAttribute("transferencia") TransferenciaForm form, BindingResult errores,
            HttpSession sesion, Model model, RedirectAttributes flash) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        if (!errores.hasErrors()) {
            try {
                transferencias.transferir(sede.get().getId(), usuario.getDatos().usuarioId(), form);
                flash.addFlashAttribute("mensaje", "Transferencia registrada en ambas sedes.");
                return "redirect:/transferencias";
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                errores.reject("concurrencia", "El inventario cambió. Vuelve a intentarlo.");
            }
        }
        model.addAttribute("error", errores.getAllErrors().getFirst().getDefaultMessage());
        return cargar(usuario, sesion, model);
    }

    private String cargar(UsuarioPrincipal usuario, HttpSession sesion, Model model) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        Long sedeId = sede.get().getId();
        model.addAttribute("existencias", inventario.listar(sedeId).stream()
                .filter(i -> i.activo() && i.stock().signum() > 0).toList());
        model.addAttribute("destinos", contexto.disponibles(usuario).stream()
                .filter(s -> !s.getId().equals(sedeId)).toList());
        model.addAttribute("transferencias", transferencias.historial(sedeId));
        if (!model.containsAttribute("transferencia")) model.addAttribute("transferencia", new TransferenciaForm());
        return "transferencias";
    }
}
