package sistema_maderera.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sistema_maderera.dto.AdministradorPlataformaForm;
import sistema_maderera.entity.AdministradorPlataforma;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.AdministradorPlataformaRepository;

@Service
public class AdministradorPlataformaService {

    private final AdministradorPlataformaRepository administradores;
    private final PasswordEncoder encoder;
    private final ValidacionService validacion;

    public AdministradorPlataformaService(
            AdministradorPlataformaRepository administradores,
            PasswordEncoder encoder,
            ValidacionService validacion) {

        this.administradores = administradores;
        this.encoder = encoder;
        this.validacion = validacion;
    }

    @Transactional
    public AdministradorPlataforma crear(AdministradorPlataformaForm form) {
        validacion.validar(form);

        String usuario = form.getUsuario().toLowerCase(Locale.ROOT);

        if (administradores.findByUsuario(usuario).isPresent()) {
            throw new ReglaNegocioException(
                    "Ese nombre de usuario ya está registrado.");
        }

        String password = form.getPassword();

        if (password.getBytes(StandardCharsets.UTF_8).length > 72
                || password.codePoints().noneMatch(Character::isLetter)
                || password.codePoints().noneMatch(Character::isDigit)) {
            throw new ReglaNegocioException(
                    "La contraseña debe incluir letras y números "
                    + "y no superar 72 bytes.");
        }

        AdministradorPlataforma administrador = new AdministradorPlataforma();
        administrador.setNombre(form.getNombre().trim());
        administrador.setUsuario(usuario);
        administrador.setPassword(encoder.encode(password));
        administrador.setEstado(true);

        return administradores.save(administrador);
    }
}