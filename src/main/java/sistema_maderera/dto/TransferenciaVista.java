package sistema_maderera.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferenciaVista(Long id, String producto, String origen, String destino,
        BigDecimal cantidad, String motivo, String usuario, LocalDateTime fecha) { }
