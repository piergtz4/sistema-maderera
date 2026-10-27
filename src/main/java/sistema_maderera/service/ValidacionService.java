package sistema_maderera.service;

import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import sistema_maderera.exception.ReglaNegocioException;

@Service
public class ValidacionService {
    private final Validator validator;

    public ValidacionService(Validator validator) { this.validator = validator; }

    public void validar(Object formulario) {
        var errores = validator.validate(formulario);
        if (!errores.isEmpty()) {
            String mensaje = errores.stream().map(e -> e.getPropertyPath() + ": " + e.getMessage())
                    .sorted().findFirst().orElseThrow();
            throw new ReglaNegocioException(mensaje);
        }
    }
}
