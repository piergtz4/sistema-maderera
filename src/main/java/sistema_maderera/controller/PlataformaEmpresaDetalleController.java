package sistema_maderera.controller;

import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.dto.PlataformaAdministradorEmpresaForm;
import sistema_maderera.dto.RestablecerClavePlataformaForm;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.AdministradorPlataformaPrincipal;
import sistema_maderera.service.EmpresaService;
import sistema_maderera.service.PlataformaAdministradorEmpresaService;
import sistema_maderera.service.SedeService;

@Controller
@RequestMapping("/plataforma/empresas/{empresaId}")
public class PlataformaEmpresaDetalleController {
    private final EmpresaService empresas;
    private final SedeService sedes;
    private final PlataformaAdministradorEmpresaService administradores;

    public PlataformaEmpresaDetalleController(EmpresaService empresas, SedeService sedes,
            PlataformaAdministradorEmpresaService administradores) {
        this.empresas = empresas;
        this.sedes = sedes;
        this.administradores = administradores;
    }

    @InitBinder("cuenta")
    public void camposDeCuenta(WebDataBinder binder) {
        binder.setAllowedFields("nombre", "usuario");
    }

    @InitBinder("clave")
    public void camposDeClave(WebDataBinder binder) {
        binder.setAllowedFields("nueva", "confirmacion", "clavePlataforma");
    }

    @GetMapping
    public String detalle(@AuthenticationPrincipal AdministradorPlataformaPrincipal actor,
            @PathVariable Long empresaId, Model model) {
        model.addAttribute("nombreAdministrador", actor.getNombre());
        model.addAttribute("empresa", empresas.buscar(empresaId));
        model.addAttribute("sedes", sedes.listar(empresaId));
        model.addAttribute("usuariosEmpresa", administradores.listarUsuarios(empresaId));
        var cuentas = administradores.listar(empresaId);
        model.addAttribute("administradores", cuentas);
        model.addAttribute("administradoresActivos", cuentas.stream().filter(c -> c.activo()).count());
        return "plataforma/empresa-detalle";
    }

    @GetMapping("/administradores/{id}/editar")
    public String editar(@AuthenticationPrincipal AdministradorPlataformaPrincipal actor,
            @PathVariable Long empresaId, @PathVariable Long id, Model model) {
        model.addAttribute("cuenta", administradores.formulario(empresaId, id));
        cargarCuenta(actor, empresaId, id, model);
        return "plataforma/administrador-editar";
    }

    @PostMapping("/administradores/{id}")
    public String guardar(@AuthenticationPrincipal AdministradorPlataformaPrincipal actor,
            @PathVariable Long empresaId, @PathVariable Long id,
            @Valid @ModelAttribute("cuenta") PlataformaAdministradorEmpresaForm form, BindingResult errores,
            Model model, RedirectAttributes flash) {
        administradores.formulario(empresaId, id);
        if (!errores.hasErrors()) {
            try {
                administradores.editar(empresaId, id, actor.getId(), form);
                flash.addFlashAttribute("mensaje", "Datos del administrador actualizados correctamente.");
                return volver(empresaId);
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            } catch (DataIntegrityViolationException e) {
                errores.reject("duplicado", "Ese nombre de usuario ya está registrado.");
            }
        }
        cargarCuenta(actor, empresaId, id, model);
        return "plataforma/administrador-editar";
    }

    @PostMapping("/administradores/{id}/estado")
    public String cambiarEstado(@AuthenticationPrincipal AdministradorPlataformaPrincipal actor,
            @PathVariable Long empresaId, @PathVariable Long id, @RequestParam boolean activo,
            RedirectAttributes flash) {
        try {
            administradores.cambiarEstado(empresaId, id, actor.getId(), activo);
            flash.addFlashAttribute("mensaje", activo ? "Administrador activado."
                    : "Acceso del administrador suspendido.");
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
        }
        return volver(empresaId);
    }

    @GetMapping("/administradores/{id}/clave")
    public String clave(@AuthenticationPrincipal AdministradorPlataformaPrincipal actor,
            @PathVariable Long empresaId, @PathVariable Long id, Model model) {
        model.addAttribute("cuenta", administradores.formulario(empresaId, id));
        model.addAttribute("clave", new RestablecerClavePlataformaForm());
        cargarCuenta(actor, empresaId, id, model);
        return "plataforma/administrador-clave";
    }

    @PostMapping("/administradores/{id}/clave")
    public String restablecer(@AuthenticationPrincipal AdministradorPlataformaPrincipal actor,
            @PathVariable Long empresaId, @PathVariable Long id,
            @Valid @ModelAttribute("clave") RestablecerClavePlataformaForm form, BindingResult errores,
            Model model, RedirectAttributes flash) {
        model.addAttribute("cuenta", administradores.formulario(empresaId, id));
        if (!errores.hasErrors()) {
            try {
                administradores.restablecerClave(empresaId, id, actor.getId(), form);
                flash.addFlashAttribute("mensaje", "Contraseña restablecida. El administrador debe iniciar sesión con la nueva contraseña.");
                return volver(empresaId);
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            }
        }
        form.setNueva(null);
        form.setConfirmacion(null);
        form.setClavePlataforma(null);
        cargarCuenta(actor, empresaId, id, model);
        return "plataforma/administrador-clave";
    }

    private void cargarCuenta(AdministradorPlataformaPrincipal actor, Long empresaId, Long id, Model model) {
        model.addAttribute("nombreAdministrador", actor.getNombre());
        model.addAttribute("nombreEmpresa", empresas.nombre(empresaId));
        model.addAttribute("empresaId", empresaId);
        model.addAttribute("administradorId", id);
    }

    private String volver(Long empresaId) {
        return "redirect:/plataforma/empresas/" + empresaId + "#administradores";
    }
}
