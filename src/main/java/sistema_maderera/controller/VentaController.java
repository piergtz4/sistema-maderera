package sistema_maderera.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.dto.DocumentoVentaForm;
import sistema_maderera.entity.TipoDocumentoVenta;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.*;

@Controller
@RequestMapping("/ventas")
public class VentaController {
    private final DocumentoVentaService documentos;
    private final ClienteService clientes;
    private final SedeContexto contexto;
    private final NumeracionDocumentoService numeracion;
    private final CuentaEmpresaService cuentas;

    public VentaController(DocumentoVentaService documentos, ClienteService clientes, SedeContexto contexto,
            NumeracionDocumentoService numeracion, CuentaEmpresaService cuentas) {
        this.documentos = documentos;
        this.clientes = clientes;
        this.contexto = contexto;
        this.numeracion = numeracion;
        this.cuentas = cuentas;
    }

    @GetMapping
    public String listar(@AuthenticationPrincipal UsuarioPrincipal usuario, HttpSession sesion, Model model) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        model.addAttribute("documentos", documentos.listar(sede.get().getId()));
        return "ventas";
    }

    @GetMapping("/nuevo")
    public String nuevo(@AuthenticationPrincipal UsuarioPrincipal usuario, HttpSession sesion, Model model) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        model.addAttribute("documento", new DocumentoVentaForm());
        return cargar(usuario, sede.get().getId(), model);
    }

    @GetMapping("/{id}/editar")
    public String editar(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            HttpSession sesion, Model model, RedirectAttributes flash) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        try {
            model.addAttribute("documento", documentos.formulario(sede.get().getId(), id));
            return cargar(usuario, sede.get().getId(), model);
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/ventas";
        }
    }

    @PostMapping("/guardar")
    public String guardar(@AuthenticationPrincipal UsuarioPrincipal usuario,
            @Valid @ModelAttribute("documento") DocumentoVentaForm form, BindingResult errores,
            HttpSession sesion, Model model, RedirectAttributes flash,
            @RequestParam(defaultValue = "guardar") String accion) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        if (!errores.hasErrors()) {
            try {
                var guardado = documentos.guardarBorrador(sede.get().getId(), usuario.getDatos().usuarioId(), form, "revisar".equals(accion));
                flash.addFlashAttribute("mensaje", "Datos guardados. Todavía no se han descontado existencias ni aplicado cobros.");
                if ("revisar".equals(accion)) return "redirect:/ventas/" + guardado.getId();
                return "redirect:/ventas";
            } catch (ReglaNegocioException e) {
                errores.reject("negocio", e.getMessage());
            } catch (OptimisticLockingFailureException e) {
                errores.reject("concurrencia", "El borrador cambió. Vuelve a abrirlo antes de guardar.");
            }
        }
        return cargar(usuario, sede.get().getId(), model);
    }

    @GetMapping("/{id}")
    public String ver(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            HttpSession sesion, Model model) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        model.addAttribute("venta", documentos.ver(sede.get().getId(), id));
        model.addAttribute("ventaVinculada", documentos.ventaDeCotizacion(sede.get().getId(), id));
        return "venta-detalle";
    }

    @PostMapping("/{id}/emitir-cotizacion")
    public String emitirCotizacion(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            @RequestParam Long version, HttpSession sesion, RedirectAttributes flash) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        try {
            var doc = documentos.emitirCotizacion(sede.get().getId(), usuario.getDatos().usuarioId(), id, version);
            flash.addFlashAttribute("mensaje", "Cotización " + doc.getCodigoDocumento() + " emitida. No reserva ni descuenta stock y no registra cobros.");
        } catch (ReglaNegocioException | OptimisticLockingFailureException e) {
            flash.addFlashAttribute("error", e instanceof ReglaNegocioException ? e.getMessage() : "El documento cambió; vuelve a abrirlo.");
        }
        return "redirect:/ventas/" + id;
    }

    @PostMapping("/{id}/convertir")
    public String convertir(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            @RequestParam Long version, @RequestParam TipoDocumentoVenta tipo, HttpSession sesion, RedirectAttributes flash) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        try {
            var venta = documentos.convertirCotizacion(sede.get().getId(), usuario.getDatos().usuarioId(), id, version, tipo);
            flash.addFlashAttribute("mensaje", "Venta vinculada abierta. Revisa los datos y completa los pagos antes de confirmar.");
            flash.addFlashAttribute("tipoMensaje", "info");
            return "redirect:/ventas/" + venta.getId() + (venta.getEstado() == sistema_maderera.entity.EstadoDocumentoVenta.BORRADOR ? "/editar" : "");
        } catch (ReglaNegocioException | OptimisticLockingFailureException e) {
            flash.addFlashAttribute("error", e instanceof ReglaNegocioException ? e.getMessage() : "El documento cambió; vuelve a abrirlo.");
            return "redirect:/ventas/" + id;
        }
    }

    @PostMapping("/{id}/descartar")
    public String descartar(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            @RequestParam Long version, HttpSession sesion, RedirectAttributes flash) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        try {
            documentos.descartarPendiente(sede.get().getId(), usuario.getDatos().usuarioId(), id, version);
            flash.addFlashAttribute("mensaje", "Documento pendiente eliminado. No se modificaron existencias ni correlativos.");
        } catch (ReglaNegocioException | OptimisticLockingFailureException e) {
            flash.addFlashAttribute("error", e instanceof ReglaNegocioException ? e.getMessage() : "El documento cambió; vuelve a abrirlo antes de descartarlo.");
        }
        return "redirect:/ventas";
    }

    @GetMapping("/{id}/imprimir")
    public String imprimir(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            HttpSession sesion, Model model, RedirectAttributes flash) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        try {
            var venta = documentos.paraImprimir(sede.get().getId(), id);
            model.addAttribute("venta", venta);
            model.addAttribute("emisor", venta.getSede().getEmpresa());
            model.addAttribute("local", venta.getSede());
            return "venta-impresion";
        } catch (ReglaNegocioException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/ventas/" + id;
        }
    }

    @PostMapping("/{id}/confirmar")
    public String confirmar(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            @RequestParam Long version, HttpSession sesion, RedirectAttributes flash) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        try {
            var venta = documentos.confirmar(sede.get().getId(), usuario.getDatos().usuarioId(), id, version);
            flash.addFlashAttribute("mensaje", "Venta " + venta.getCodigoDocumento() + " confirmada. Stock y pagos registrados.");
        } catch (ReglaNegocioException | OptimisticLockingFailureException e) {
            flash.addFlashAttribute("error", e instanceof ReglaNegocioException ? e.getMessage() : "El documento cambió; revísalo antes de confirmar.");
        }
        return "redirect:/ventas/" + id;
    }

    @PostMapping("/{id}/anular")
    public String anular(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            @RequestParam Long version, @RequestParam String motivo, HttpSession sesion, RedirectAttributes flash) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        try {
            var venta = documentos.anular(sede.get().getId(), usuario.getDatos().usuarioId(), id, version, motivo);
            flash.addFlashAttribute("mensaje", "Venta " + venta.getCodigoDocumento() + " anulada. Existencias repuestas y cobros sin efecto.");
        } catch (ReglaNegocioException | OptimisticLockingFailureException e) {
            flash.addFlashAttribute("error", e instanceof ReglaNegocioException ? e.getMessage() : "El documento cambió; revísalo antes de anular.");
        }
        return "redirect:/ventas/" + id;
    }

    @PostMapping("/{id}/copiar")
    public String copiar(@AuthenticationPrincipal UsuarioPrincipal usuario, @PathVariable Long id,
            @RequestParam Long version, HttpSession sesion, RedirectAttributes flash) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        try {
            var copia = documentos.copiarAnulado(sede.get().getId(), usuario.getDatos().usuarioId(), id, version);
            flash.addFlashAttribute("mensaje", "Copia pendiente abierta. Revisa cliente, productos y abonos, incluidos sus códigos. El nuevo número se asignará al confirmar.");
            flash.addFlashAttribute("tipoMensaje", "info");
            return "redirect:/ventas/" + copia.getId() + "/editar";
        } catch (ReglaNegocioException | OptimisticLockingFailureException e) {
            flash.addFlashAttribute("error", e instanceof ReglaNegocioException ? e.getMessage() : "El documento cambió; vuelve a abrirlo.");
            return "redirect:/ventas/" + id;
        }
    }

    private String cargar(UsuarioPrincipal usuario, Long sedeId, Model model) {
        if (model.getAttribute("documento") instanceof DocumentoVentaForm form && form.getId() != null) {
            var doc = documentos.ver(sedeId, form.getId());
            model.addAttribute("documentoOrigenId", doc.getDocumentoOrigenId());
            model.addAttribute("codigoOrigen", doc.getCodigoOrigen());
        }
        model.addAttribute("clientes", clientes.listar(usuario.getDatos().empresaId()));
        model.addAttribute("cuentasPago", cuentas.listar(usuario.getDatos().empresaId()));
        model.addAttribute("tiposDocumento", TipoDocumentoVenta.values());
        model.addAttribute("numeracion", numeracion.proximos(sedeId));
        model.addAttribute("productosVenta", documentos.productosDisponibles(sedeId));
        return "venta-form";
    }
}
