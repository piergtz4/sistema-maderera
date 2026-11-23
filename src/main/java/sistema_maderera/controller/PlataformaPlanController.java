package sistema_maderera.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.dto.PlanEmpresaForm;
import sistema_maderera.entity.PeriodicidadPlan;
import sistema_maderera.entity.PlanComercial;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.AdministradorPlataformaPrincipal;
import sistema_maderera.service.EmpresaService;
import sistema_maderera.service.PlanEmpresaService;

@Controller
@RequestMapping("/plataforma/empresas/{empresaId}/plan")
public class PlataformaPlanController {
    private final EmpresaService empresas;
    private final PlanEmpresaService planes;

    public PlataformaPlanController(EmpresaService empresas, PlanEmpresaService planes) {
        this.empresas = empresas;
        this.planes = planes;
    }

    @InitBinder("contrato")
    public void camposPermitidos(WebDataBinder binder) {
        binder.setAllowedFields("plan", "periodicidad", "sedesAdicionales", "usuariosAdicionales", "version");
    }

    @GetMapping
    public String configurar(@PathVariable Long empresaId,
            @AuthenticationPrincipal AdministradorPlataformaPrincipal actor, Model model) {
        model.addAttribute("contrato", planes.formulario(empresaId));
        cargar(empresaId, actor, model);
        return "plataforma/empresa-plan";
    }

    @PostMapping
    public String guardar(@PathVariable Long empresaId,
            @AuthenticationPrincipal AdministradorPlataformaPrincipal actor,
            @Valid @ModelAttribute("contrato") PlanEmpresaForm form, BindingResult errores,
            Model model, RedirectAttributes flash) {
        empresas.buscar(empresaId);
        if (!errores.hasErrors()) {
            try {
                planes.guardar(empresaId, actor.getId(), form);
                flash.addFlashAttribute("mensaje", "Plan y adicionales guardados correctamente.");
                return "redirect:/plataforma/empresas/" + empresaId + "#plan";
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            }
        }
        cargar(empresaId, actor, model);
        return "plataforma/empresa-plan";
    }

    private void cargar(Long empresaId, AdministradorPlataformaPrincipal actor, Model model) {
        model.addAttribute("empresa", empresas.buscar(empresaId));
        model.addAttribute("nombreAdministrador", actor.getNombre());
        model.addAttribute("planes", PlanComercial.values());
        model.addAttribute("periodicidades", PeriodicidadPlan.values());
        model.addAttribute("precioSede", PlanEmpresaService.PRECIO_SEDE);
        model.addAttribute("precioUsuario", PlanEmpresaService.PRECIO_USUARIO);
    }
}
