package sistema_maderera.util;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component("formatoCantidad")
public class FormatoCantidad {
    public String mostrar(BigDecimal cantidad) {
        return cantidad.stripTrailingZeros().toPlainString();
    }
}
