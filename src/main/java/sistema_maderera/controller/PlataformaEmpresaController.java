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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.dto.PlataformaEmpresaForm;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.AdministradorPlataformaPrincipal;
import sistema_maderera.service.PlataformaEmpresaService;

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
                "sede.telefono", "nombreAdministrador", "usuario", "password", "confirmarPassword");
    }

    @GetMapping("/nueva")
    public String nueva(@AuthenticationPrincipal AdministradorPlataformaPrincipal administrador, Model model) {
        model.addAttribute("nombreAdministrador", administrador.getNombre());
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
                        + " registrada con su primera sede y administrador general.");
                return "redirect:/plataforma";
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                errores.reject("duplicado", "El RUC o el usuario ya se encuentra registrado.");
            }
        }
        form.setPassword(null);
        form.setConfirmarPassword(null);
        model.addAttribute("nombreAdministrador", administrador.getNombre());
        return "plataforma/empresa-form";
    }
}
