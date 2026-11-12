package sistema_maderera.service;

import java.util.Locale;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sistema_maderera.repository.AdministradorPlataformaRepository;
import sistema_maderera.security.AdministradorPlataformaPrincipal;

@Service
public class PlataformaAuthService implements UserDetailsService {

    private final AdministradorPlataformaRepository administradores;

    public PlataformaAuthService(
            AdministradorPlataformaRepository administradores) {
        this.administradores = administradores;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        String usuario = username.trim().toLowerCase(Locale.ROOT);

        return administradores.findByUsuario(usuario)
                .map(AdministradorPlataformaPrincipal::new)
                .orElseThrow(() ->
                        new UsernameNotFoundException(
                                "Credenciales incorrectas."));
    }
}