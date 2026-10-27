package sistema_maderera.controller;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.dto.EmpresaForm;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.EmpresaService;
import sistema_maderera.service.CuentaEmpresaService;
import sistema_maderera.dto.CuentaEmpresaForm;

@Controller
public class EmpresaController {
    private final EmpresaService empresas;
    private final CuentaEmpresaService cuentas;

    public EmpresaController(EmpresaService empresas, CuentaEmpresaService cuentas) { this.empresas = empresas; this.cuentas = cuentas; }

    private void cargarCuentas(Long id, Model model) {
        model.addAttribute("cuentas", cuentas.listar(id));
        model.addAttribute("bancos", CuentaEmpresaService.BANCOS);
        if (!model.containsAttribute("cuenta")) model.addAttribute("cuenta", new CuentaEmpresaForm());
    }

    @PostMapping("/configuracion/cuentas")
    public String agregarCuenta(@AuthenticationPrincipal UsuarioPrincipal usuario,
            @Valid @ModelAttribute("cuenta") CuentaEmpresaForm form, BindingResult errores, Model model, RedirectAttributes flash) {
        Long id = usuario.getDatos().empresaId();
        if (!errores.hasErrors()) {
            try {
                cuentas.agregar(id, form);
                flash.addFlashAttribute("mensaje", "Cuenta agregada. Ya está disponible en ventas.");
                return "redirect:/configuracion#cuentas";
            } catch (ReglaNegocioException e) { errores.reject("cuenta", e.getMessage()); }
        }
        model.addAttribute("empresa", empresas.formulario(id));
        model.addAttribute("tieneLogo", empresas.tieneLogo(id));
        cargarCuentas(id, model);
        return "configuracion";
    }

    @PostMapping("/configuracion/cuentas/{id}/estado")
    public String estadoCuenta(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            @RequestParam boolean activa, RedirectAttributes flash) {
        try {
            cuentas.estado(usuario.getDatos().empresaId(), id, activa);
            flash.addFlashAttribute("mensaje", "Estado de la cuenta actualizado.");
        } catch (ReglaNegocioException e) { flash.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/configuracion#cuentas";
    }

    @GetMapping("/configuracion")
    public String configuracion(@AuthenticationPrincipal UsuarioPrincipal usuario, Model model) {
        Long id = usuario.getDatos().empresaId();
        model.addAttribute("empresa", empresas.formulario(id));
        model.addAttribute("tieneLogo", empresas.tieneLogo(id));
        cargarCuentas(id, model);
        return "configuracion";
    }

    @PostMapping("/configuracion")
    public String guardar(@AuthenticationPrincipal UsuarioPrincipal usuario,
            @Valid @ModelAttribute("empresa") EmpresaForm form, BindingResult errores,
            @RequestParam(name = "logo", required = false) MultipartFile logo,
            Model model, RedirectAttributes flash) {
        if (!errores.hasErrors()) {
            try {
                empresas.guardar(usuario.getDatos().empresaId(), form, logo);
                flash.addFlashAttribute("mensaje", "Configuración de la empresa actualizada.");
                return "redirect:/configuracion";
            } catch (ReglaNegocioException e) {
                errores.reject("empresa", e.getMessage());
            }
        }
        model.addAttribute("tieneLogo", empresas.tieneLogo(usuario.getDatos().empresaId()));
        cargarCuentas(usuario.getDatos().empresaId(), model);
        return "configuracion";
    }

    @GetMapping("/empresa/logo")
    public ResponseEntity<byte[]> logo(@AuthenticationPrincipal UsuarioPrincipal usuario) {
        var logo = empresas.logo(usuario.getDatos().empresaId());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(logo.tipo())).body(logo.datos());
    }
}
