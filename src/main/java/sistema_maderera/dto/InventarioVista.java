package sistema_maderera.dto;

import java.math.BigDecimal;

public record InventarioVista(Long id, String codigo, String nombre, String categoria, String sede, String unidad,
        BigDecimal stock, BigDecimal stockMinimo, Boolean activo) {
    public boolean isStockBajo() { return stock.compareTo(stockMinimo) <= 0; }

    public String getSituacion() {
        if (!Boolean.TRUE.equals(activo)) return "Inactivo";
        if (stock.signum() == 0) return "Sin stock";
        return isStockBajo() ? "Stock bajo" : "Disponible";
    }

    public String getTextoBusqueda() {
        return String.join(" ", codigo, nombre, categoria, sede, unidad,
                stock.stripTrailingZeros().toPlainString(), getSituacion());
    }
}
