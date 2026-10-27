package sistema_maderera;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import sistema_maderera.dto.InventarioVista;

import static org.assertj.core.api.Assertions.assertThat;

class InventarioVistaTests {
    @Test
    void stockEnMinimo() {
        var inventario = new InventarioVista(1L, "PRO-000001", "Melamina", "Planchas",
                "Sede Parcona", "UND", new BigDecimal("10"), new BigDecimal("10"), true);

        assertThat(inventario.getSituacion()).isEqualTo("Stock bajo");
    }

    @ParameterizedTest
    @CsvSource({
            "9, 10, true, Stock bajo",
            "10.0000, 10, true, Stock bajo",
            "11, 10, true, Disponible",
            "0, 10, true, Sin stock",
            "0, 0, true, Sin stock",
            "10, 10, false, Inactivo"
    })
    void clasificacionStock(String stock, String minimo, boolean activo, String esperado) {
        var inventario = new InventarioVista(1L, "PRO-000001", "Melamina", "Planchas",
                "Sede Parcona", "UND", new BigDecimal(stock), new BigDecimal(minimo), activo);

        assertThat(inventario.getSituacion()).isEqualTo(esperado);
    }
}
