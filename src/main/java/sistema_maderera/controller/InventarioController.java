package sistema_maderera.controller;

import jakarta.servlet.http.HttpSession;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.InventarioService;
import sistema_maderera.service.EmpresaService;
import sistema_maderera.service.ReporteInventarioService;
import sistema_maderera.service.SedeContexto;

@Controller
@RequestMapping("/inventario")
public class InventarioController {
    private final InventarioService inventario;
    private final SedeContexto contexto;
    private final ReporteInventarioService reportes;
    private final EmpresaService empresas;

    public InventarioController(InventarioService inventario, SedeContexto contexto,
            ReporteInventarioService reportes, EmpresaService empresas) {
        this.inventario = inventario;
        this.contexto = contexto;
        this.reportes = reportes;
        this.empresas = empresas;
    }

    @GetMapping
    public String listar(@AuthenticationPrincipal UsuarioPrincipal usuario, HttpSession sesion, Model model) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return "redirect:/seleccionar-sede";
        model.addAttribute("existencias", inventario.listar(sede.get().getId()));
        return "inventario";
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(@AuthenticationPrincipal UsuarioPrincipal usuario, HttpSession sesion,
            @RequestParam(defaultValue = "") String buscar, @RequestParam(required = false) Boolean activo) {
        var sede = contexto.actual(usuario, sesion);
        if (sede.isEmpty()) return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(URI.create("/seleccionar-sede")).build();
        var datos = usuario.getDatos();
        var archivo = reportes.generar(sede.get().getId(), datos.usuarioId(), empresas.nombre(datos.empresaId()),
                sede.get().getNombre(), buscar, activo);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .cacheControl(CacheControl.noStore())
                .contentLength(archivo.contenido().length)
                .body(archivo.contenido());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public String errorReporte(ReglaNegocioException e, RedirectAttributes flash) {
        flash.addFlashAttribute("error", e.getMessage());
        return "redirect:/inventario";
    }
}
