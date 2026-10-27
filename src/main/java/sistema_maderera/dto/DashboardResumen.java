package sistema_maderera.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResumen(long productos, long sinStock, long stockBajo, BigDecimal valor,
        List<InventarioVista> alertas, List<MovimientoVista> movimientos) {
}
