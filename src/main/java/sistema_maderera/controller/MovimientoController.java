package sistema_maderera.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.InventarioService;
import sistema_maderera.service.SedeContexto;

@Controller
@RequestMapping("/movimientos")
public class MovimientoController {
    private final InventarioService inventario;
    private final SedeContexto contexto;

    public MovimientoController(InventarioService inventario, SedeContexto contexto) {
        this.inventario = inventario;
        this.contexto = contexto;
    }

    @GetMapping
    public String listar(@AuthenticationPrincipal UsuarioPrincipal usuario, HttpSession sesion, Model model) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        model.addAttribute("movimientos", inventario.historial(sede.get().getId()));
        return "movimientos";
    }
}
