package sistema_maderera.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.InventarioService;
import sistema_maderera.service.SedeContexto;
import sistema_maderera.service.DashboardService;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {
    private final InventarioService inventario;
    private final SedeContexto contexto;
    private final DashboardService dashboard;

    public DashboardController(InventarioService inventario, SedeContexto contexto, DashboardService dashboard) {
        this.inventario = inventario;
        this.contexto = contexto;
        this.dashboard = dashboard;
    }

    @GetMapping("/dashboard")
    public String mostrar(@AuthenticationPrincipal UsuarioPrincipal usuario, HttpSession sesion, Model model,
            @RequestParam(defaultValue = "hoy") String periodo, @RequestParam(defaultValue = "") String desde,
            @RequestParam(defaultValue = "") String hasta) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return contexto.esAdministradorGeneral(usuario)
                ? "redirect:/seleccionar-sede" : "error/403";
        model.addAttribute("resumen", inventario.resumen(sede.get().getId()));
        DashboardService.Periodo fechas;
        try { fechas = dashboard.periodo(periodo, desde, hasta); }
        catch (IllegalArgumentException e) { model.addAttribute("advertencia", e.getMessage()); fechas = dashboard.periodo("hoy", "", ""); }
        model.addAttribute("periodo", fechas);
        model.addAttribute("indicadores", dashboard.informe(sede.get().getId(), fechas));
        return "dashboard";
    }
}
