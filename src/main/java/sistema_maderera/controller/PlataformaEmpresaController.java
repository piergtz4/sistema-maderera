package sistema_maderera.controller;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.dto.PlataformaEmpresaForm;
import sistema_maderera.dto.EmpresaForm;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.AdministradorPlataformaPrincipal;
import sistema_maderera.service.PlataformaEmpresaService;
import sistema_maderera.service.PlanEmpresaService;
import sistema_maderera.entity.PlanComercial;
import sistema_maderera.entity.PeriodicidadPlan;

@Controller
@RequestMapping("/plataforma/empresas")
public class PlataformaEmpresaController {
    private final PlataformaEmpresaService empresas;

    public PlataformaEmpresaController(PlataformaEmpresaService empresas) {
        this.empresas = empresas;
    }

    @InitBinder("alta")
    public void camposPermitidos(WebDataBinder binder) {
        binder.setAllowedFields("empresa.nombreComercial", "empresa.razonSocial", "empresa.ruc",
                "empresa.email", "empresa.telefono", "empresa.direccion", "sede.nombre",
                "sede.departamento", "sede.provincia", "sede.distrito", "sede.direccion",
                "sede.telefono", "nombreAdministrador", "usuario", "password", "confirmarPassword",
                "plan", "periodicidad", "sedesAdicionales", "usuariosAdicionales");
    }

    @InitBinder("empresa")
    public void camposDeEdicion(WebDataBinder binder) {
        binder.setAllowedFields("nombreComercial", "razonSocial", "ruc", "email", "telefono", "direccion");
    }

    @GetMapping("/{id}/editar")
    public String editar(@AuthenticationPrincipal AdministradorPlataformaPrincipal administrador,
            @PathVariable Long id, Model model) {
        model.addAttribute("empresa", empresas.formulario(id));
        cargarEdicion(administrador, id, model);
        return "plataforma/empresa-editar";
    }

    @PostMapping("/{id}")
    public String guardar(@AuthenticationPrincipal AdministradorPlataformaPrincipal administrador,
            @PathVariable Long id, @Valid @ModelAttribute("empresa") EmpresaForm form,
            BindingResult errores, Model model, RedirectAttributes flash) {
        if (!errores.hasErrors()) {
            try {
                empresas.editar(id, form);
                flash.addFlashAttribute("mensaje", "Datos de la empresa actualizados correctamente.");
                return "redirect:/plataforma?estado=todas";
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                errores.reject("duplicado", "Ese RUC ya pertenece a una empresa registrada.");
            }
        }
        cargarEdicion(administrador, id, model);
        return "plataforma/empresa-editar";
    }

    @PostMapping("/{id}/estado")
    public String cambiarEstado(@PathVariable Long id, @RequestParam boolean activo, RedirectAttributes flash) {
        empresas.cambiarEstado(id, activo);
        flash.addFlashAttribute("mensaje", activo
                ? "Empresa reactivada. Sus usuarios pueden volver a iniciar sesión."
                : "Empresa suspendida. Sus usuarios ya no pueden acceder al sistema.");
        return "redirect:/plataforma?estado=" + (activo ? "activas" : "suspendidas");
    }

    private void cargarEdicion(AdministradorPlataformaPrincipal administrador, Long id, Model model) {
        model.addAttribute("nombreAdministrador", administrador.getNombre());
        model.addAttribute("empresaId", id);
    }

    @GetMapping("/nueva")
    public String nueva(@AuthenticationPrincipal AdministradorPlataformaPrincipal administrador, Model model) {
        cargarAlta(administrador, model);
        model.addAttribute("alta", new PlataformaEmpresaForm());
        return "plataforma/empresa-form";
    }

    @PostMapping
    public String crear(@AuthenticationPrincipal AdministradorPlataformaPrincipal administrador,
            @Valid @ModelAttribute("alta") PlataformaEmpresaForm form, BindingResult errores,
            Model model, RedirectAttributes flash) {
        if (!errores.hasErrors()) {
            try {
                var empresa = empresas.crear(form);
                flash.addFlashAttribute("mensaje", "Empresa " + empresa.getNombreComercial()
                        + " registrada con el plan " + empresa.getPlanComercial().getNombre() + ", su primera sede y administrador general.");
                return "redirect:/plataforma";
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                errores.reject("duplicado", "El RUC o el usuario ya se encuentra registrado.");
            }
        }
        form.setPassword(null);
        form.setConfirmarPassword(null);
        cargarAlta(administrador, model);
        return "plataforma/empresa-form";
    }

    private void cargarAlta(AdministradorPlataformaPrincipal administrador, Model model) {
        model.addAttribute("nombreAdministrador", administrador.getNombre());
        model.addAttribute("planes", PlanComercial.values());
        model.addAttribute("periodicidades", PeriodicidadPlan.values());
        model.addAttribute("precioSede", PlanEmpresaService.PRECIO_SEDE);
        model.addAttribute("precioUsuario", PlanEmpresaService.PRECIO_USUARIO);
    }
}
