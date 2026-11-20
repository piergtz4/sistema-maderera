package sistema_maderera.service;

import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import sistema_maderera.exception.ReglaNegocioException;

@Service
public class PoliticaContrasenaService {
    public void validar(String clave) {
        if (clave == null || clave.length() < 12
                || clave.codePoints().noneMatch(Character::isLetter)
                || clave.codePoints().noneMatch(Character::isDigit)) {
            throw new ReglaNegocioException("La contraseña debe tener al menos 12 caracteres e incluir letras y números.");
        }
        if (clave.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ReglaNegocioException("La contraseña es demasiado larga. Usa una más corta.");
        }
    }

    public void validar(String clave, String confirmacion) {
        validar(clave);
        if (!clave.equals(confirmacion)) {
            throw new ReglaNegocioException("Las contraseñas no coinciden.");
        }
    }
}
