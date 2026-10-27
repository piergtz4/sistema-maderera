package sistema_maderera.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import sistema_maderera.entity.TipoMovimiento;

public record MovimientoVista(Long id, String producto, TipoMovimiento tipo, BigDecimal cantidad,
        BigDecimal stockAnterior, BigDecimal stockPosterior, String motivo, String usuario, LocalDateTime fecha) {
}
