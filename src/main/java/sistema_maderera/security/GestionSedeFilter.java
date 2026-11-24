package sistema_maderera.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import sistema_maderera.service.AccesoSedeService;
import sistema_maderera.service.SedeContexto;

/** Comprueba el permiso de gestión antes de abrir formularios o modificar datos. */
public class GestionSedeFilter extends OncePerRequestFilter {
    private final SedeContexto sedes;
    private final AccesoSedeService accesos;

    public GestionSedeFilter(SedeContexto sedes, AccesoSedeService accesos) {
        this.sedes = sedes; this.accesos = accesos;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UsuarioPrincipal usuario
                && !sedes.esAdministradorGeneral(usuario) && requiereGestion(request)) {
            var sede = sedes.actual(usuario, request.getSession(false));
            if (sede.isEmpty() || !accesos.puedeGestionar(usuario, sede.get().getId())) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Solo tienes permiso de consulta en esta sede.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private boolean requiereGestion(HttpServletRequest request) {
        String ruta = request.getRequestURI().substring(request.getContextPath().length());
        boolean comercial = ruta.matches("/(compras|proveedores|ventas|clientes)(/.*)?");
        if ("POST".equals(request.getMethod()))
            return comercial || ruta.matches("/productos/[^/]+/inventario");
        return "GET".equals(request.getMethod()) && (ruta.matches("/productos/editar/[^/]+")
                || comercial && (ruta.endsWith("/nuevo") || ruta.endsWith("/nueva") || ruta.endsWith("/editar")));
    }
}
