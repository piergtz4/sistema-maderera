package sistema_maderera.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import sistema_maderera.service.UsuarioService;

public class SesionVigenteFilter extends OncePerRequestFilter {
    private final UsuarioService usuarios;

    public SesionVigenteFilter(UsuarioService usuarios) { this.usuarios = usuarios; }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getServletPath();
        return ruta.equals("/") || ruta.equals("/login") || ruta.equals("/error")
                || ruta.startsWith("/css/") || ruta.startsWith("/js/") || ruta.startsWith("/img/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioPrincipal principal
                && !usuarios.sesionVigente(principal)) {
            HttpSession sesion = request.getSession(false);
            if (sesion != null) sesion.invalidate();
            SecurityContextHolder.clearContext();
            response.sendRedirect(request.getContextPath() + "/?sesion");
            return;
        }
        chain.doFilter(request, response);
    }
}
