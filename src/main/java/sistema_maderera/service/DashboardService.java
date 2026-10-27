package sistema_maderera.service;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.entity.MedioPago;
import sistema_maderera.repository.*;

/**
 * Construye indicadores comerciales por período y alertas del inventario actual.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {
    private final DocumentoVentaRepository ventas;
    private final ProductoRepository productos;
    private final InventarioRepository inventarios;
    private final CuentaEmpresaRepository cuentas;
    private final Clock clock;
    public DashboardService(DocumentoVentaRepository ventas, ProductoRepository productos, InventarioRepository inventarios,
            CuentaEmpresaRepository cuentas, Clock clock) {
        this.ventas = ventas; this.productos = productos; this.inventarios = inventarios; this.cuentas = cuentas; this.clock = clock;
    }
    /**
     * Valida fechas inclusivas y determina la agrupación del gráfico. Admite como máximo 1826 días de diferencia entre fechas.
     *
     * @param filtro hoy, semana, mes, anio o personalizado
     * @param desde fecha inicial ISO para el filtro personalizado
     * @param hasta fecha final ISO para el filtro personalizado
     * @return período validado
     * @throws IllegalArgumentException si el filtro o las fechas no son válidos
     */
    public Periodo periodo(String filtro, String desde, String hasta) {
        LocalDate hoy = LocalDate.now(clock), inicio = hoy, fin = hoy;
        String titulo;
        switch (filtro) {
            case "hoy" -> titulo = "Hoy";
            case "semana" -> { inicio = hoy.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); fin = inicio.plusDays(6); titulo = "Esta semana"; }
            case "mes" -> { inicio = hoy.withDayOfMonth(1); fin = hoy.with(TemporalAdjusters.lastDayOfMonth()); titulo = "Este mes"; }
            case "anio" -> { inicio = hoy.withDayOfYear(1); fin = inicio.plusYears(1).minusDays(1); titulo = "Este año"; }
            case "personalizado" -> {
                try { inicio = LocalDate.parse(desde); fin = LocalDate.parse(hasta); }
                catch (RuntimeException e) { throw new IllegalArgumentException("Selecciona las fechas Desde y Hasta."); }
                titulo = "Período personalizado";
            }
            default -> throw new IllegalArgumentException("Selecciona un período válido.");
        }
        if (fin.isBefore(inicio) || inicio.getYear() < 2000 || fin.getYear() > 9998 || ChronoUnit.DAYS.between(inicio, fin) > 1826)
            throw new IllegalArgumentException("Revisa las fechas: Desde debe ser anterior o igual a Hasta y el rango no debe superar cinco años.");
        String agrupacion = filtro.equals("hoy") ? "hora" : filtro.equals("anio") || ChronoUnit.DAYS.between(inicio, fin) > 62 ? "mes" : "dia";
        return new Periodo(filtro, inicio, fin, titulo, agrupacion);
    }
    /**
     * Resume ventas confirmadas y ordena los cinco primeros productos por importe vendido. Las alertas corresponden al stock actual, no al stock histórico del período.
     *
     * @param sedeId identificador de la sede de trabajo autorizada
     * @param periodo rango y agrupación previamente validados
     * @return informe para el dashboard
     */
    public Informe informe(Long sedeId, Periodo periodo) {
        var documentos = ventas.ventasDelPeriodo(sedeId, periodo.desde().atStartOfDay(), periodo.hasta().plusDays(1).atStartOfDay());
        var serie = new LinkedHashMap<LocalDateTime, BigDecimal>();
        LocalDateTime paso = periodo.desde().atStartOfDay();
        if (periodo.agrupacion().equals("mes")) paso = paso.withDayOfMonth(1);
        while (paso.isBefore(periodo.hasta().plusDays(1).atStartOfDay())) {
            serie.put(paso, BigDecimal.ZERO);
            paso = switch (periodo.agrupacion()) { case "hora" -> paso.plusHours(1); case "mes" -> paso.plusMonths(1); default -> paso.plusDays(1); };
        }
        var ranking = new HashMap<String, ProductoVendido>();
        BigDecimal total = BigDecimal.ZERO;
        for (var doc : documentos) {
            total = total.add(doc.getTotal());
            LocalDateTime fecha = doc.getConfirmadoEn();
            LocalDateTime clave = switch (periodo.agrupacion()) {
                case "hora" -> fecha.withMinute(0).withSecond(0).withNano(0);
                case "mes" -> fecha.toLocalDate().withDayOfMonth(1).atStartOfDay();
                default -> fecha.toLocalDate().atStartOfDay();
            };
            serie.merge(clave, doc.getTotal(), (actual, importe) -> actual.add(importe));
            for (var l : doc.getLineas()) {
                String key = l.getProducto().getId() + "|" + l.getUnidadMedida();
                var anterior = ranking.get(key);
                ranking.put(key, new ProductoVendido(l.getCodigo(), l.getNombre(), l.getUnidadMedida(),
                        l.getCantidad().add(anterior == null ? BigDecimal.ZERO : anterior.cantidad()),
                        l.getImporteCobrar().add(anterior == null ? BigDecimal.ZERO : anterior.importe())));
            }
        }
        var formato = DateTimeFormatter.ofPattern(switch (periodo.agrupacion()) { case "hora" -> "HH:mm"; case "mes" -> "MMM yyyy"; default -> "dd/MM"; }, Locale.forLanguageTag("es-PE"));
        var puntos = serie.entrySet().stream().map(e -> new Punto(e.getKey().format(formato), e.getValue())).toList();
        var mejores = ranking.values().stream().sorted(Comparator.comparing((ProductoVendido p) -> p.importe()).reversed().thenComparing(p -> p.codigo())).limit(5).toList();
        var existencias = inventarios.listarPorSede(sedeId);
        var activos = existencias.stream().filter(i -> Boolean.TRUE.equals(i.getProducto().getEstado())).toList();
        long cero = activos.stream().filter(i -> i.getStock().signum() == 0).count();
        long bajo = activos.stream().filter(i -> i.getStock().signum() > 0 && i.getStock().compareTo(i.getStockMinimo()) <= 0).count();
        var alertas = activos.stream().filter(i -> i.getStock().signum() == 0 || i.getStock().compareTo(i.getStockMinimo()) <= 0)
                .map(i -> new Alerta(i.getProducto().getNombre(), i.getProducto().getUnidadMedida(), i.getStock(), i.getStock().signum() == 0 ? "Sin stock" : "Stock bajo")).toList();
        return new Informe(documentos.size(), total, puntos, mejores, existencias.size(), productos.count(), cero, bajo,
                activos.size() - cero - bajo, alertas, cobros(sedeId, periodo));
    }

    private List<Cobro> cobros(Long sedeId, Periodo periodo) {
        var resumen = new LinkedHashMap<String, Cobro>();
        resumen.put("EFECTIVO", new Cobro("Efectivo", "efectivo", "", BigDecimal.ZERO, 0));
        resumen.put("YAPE", new Cobro("Yape", "yape", "", BigDecimal.ZERO, 0));
        var bancos = new TreeSet<String>();
        bancos.addAll(cuentas.bancosActivosPorSede(sedeId));
        var importes = ventas.cobrosDelPeriodo(sedeId, periodo.desde().atStartOfDay(), periodo.hasta().plusDays(1).atStartOfDay());
        for (var cobro : importes) if (cobro.getMedio() == MedioPago.TRANSFERENCIA) bancos.add(nombreBanco(cobro.getBanco()));
        bancos.stream().sorted(Comparator.comparingInt((String banco) -> {
            int orden = CuentaEmpresaService.BANCOS.indexOf(banco);
            return orden < 0 ? Integer.MAX_VALUE : orden;
        }).thenComparing(banco -> banco)).forEach(banco ->
                resumen.put("BANCO:" + banco, new Cobro(banco, temaBanco(banco), "Transferencias", BigDecimal.ZERO, 0)));
        for (var cobro : importes) {
            String clave = cobro.getMedio() == MedioPago.TRANSFERENCIA ? "BANCO:" + nombreBanco(cobro.getBanco()) : cobro.getMedio().name();
            var actual = resumen.get(clave);
            resumen.put(clave, new Cobro(actual.nombre(), actual.tema(), actual.tipo(),
                    actual.monto().add(cobro.getMonto()), actual.pagos() + cobro.getPagos()));
        }
        return List.copyOf(resumen.values());
    }

    private String nombreBanco(String banco) {
        return banco == null || banco.isBlank() ? "Banco sin especificar" : banco.trim().toUpperCase(Locale.ROOT);
    }

    private String temaBanco(String banco) {
        return switch (banco) {
            case "BCP" -> "bcp";
            case "INTERBANK" -> "interbank";
            case "BBVA" -> "bbva";
            case "SCOTIABANK" -> "scotiabank";
            case "BANCO DE LA NACION" -> "nacion";
            case "BANBIF" -> "banbif";
            case "PICHINCHA" -> "pichincha";
            default -> "transferencia";
        };
    }
    public record Periodo(String filtro, LocalDate desde, LocalDate hasta, String titulo, String agrupacion) { }
    public record Punto(String etiqueta, BigDecimal monto) { }
    public record ProductoVendido(String codigo, String nombre, String unidad, BigDecimal cantidad, BigDecimal importe) { }
    public record Alerta(String nombre, String unidad, BigDecimal stock, String situacion) { }
    public record Cobro(String nombre, String tema, String tipo, BigDecimal monto, long pagos) { }
    public record Informe(long cantidad, BigDecimal total, List<Punto> puntos, List<ProductoVendido> mejores,
            long productosSede, long productosEmpresa, long sinStock, long stockBajo, long disponibles,
            List<Alerta> alertas, List<Cobro> cobros) { }
}
