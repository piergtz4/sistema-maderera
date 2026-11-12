package sistema_maderera.security;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

import sistema_maderera.entity.AdministradorPlataforma;

public class AdministradorPlataformaPrincipal extends User {

    private final Long id;
    private final String nombre;

    public AdministradorPlataformaPrincipal(
            AdministradorPlataforma administrador) {

        super(
            administrador.getUsuario(),
            administrador.getPassword(),
            Boolean.TRUE.equals(administrador.getEstado()),
            true,
            true,
            true,
            List.of(new SimpleGrantedAuthority(
                    "ROLE_ADMINISTRADOR_PLATAFORMA"))
        );

        this.id = administrador.getId();
        this.nombre = administrador.getNombre();
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
}