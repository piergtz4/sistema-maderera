package sistema_maderera.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sistema_maderera.dto.InventarioVista;
import sistema_maderera.exception.ReglaNegocioException;

@Service
public class ReporteInventarioService {
    private static final Logger log = LoggerFactory.getLogger(ReporteInventarioService.class);
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String[] COLUMNAS = {"Código", "Producto", "Categoría", "Sede", "Unidad",
            "Stock", "Stock mínimo", "Estado", "Situación"};
    private static final int FILA_CABECERA = 6;
    private final InventarioService inventario;
    private final Clock clock;

    public ReporteInventarioService(InventarioService inventario, Clock clock) {
        this.inventario = inventario;
        this.clock = clock;
    }

    /** Genera el Excel de la sede autorizada, aplicando los filtros del listado. */
    public Archivo generar(long sedeId, long usuarioId, String empresa, String sede, String buscar, Boolean activo) {
        try {
            String consulta = normalizar(buscar);
            List<InventarioVista> filas = inventario.listar(sedeId).stream()
                    .filter(i -> activo == null || activo.equals(i.activo()))
                    .filter(i -> normalizar(i.getTextoBusqueda()).contains(consulta)).toList();
            LocalDateTime fecha = LocalDateTime.now(clock);
            byte[] contenido = crearLibro(empresa, sede, buscar, activo, fecha, filas);
            String nombre = "inventario-sede-" + sedeId + "-"
                    + fecha.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".xlsx";
            log.info("Excel de inventario generado: usuarioId={}, sedeId={}, registros={}",
                    usuarioId, sedeId, filas.size());
            return new Archivo(contenido, nombre);
        } catch (IOException | RuntimeException e) {
            log.error("Error al generar Excel de inventario: usuarioId={}, sedeId={}", usuarioId, sedeId, e);
            throw new ReglaNegocioException("No se pudo generar el Excel. Intenta nuevamente en unos momentos.");
        }
    }

    private String normalizar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private byte[] crearLibro(String empresa, String sede, String buscar, Boolean activo,
            LocalDateTime fecha, List<InventarioVista> filas) throws IOException {
        try (var libro = new XSSFWorkbook(); var salida = new ByteArrayOutputStream()) {
            Sheet hoja = libro.createSheet("Inventario");
            hoja.setDisplayGridlines(false);
            CellStyle titulo = estilo(libro, true, IndexedColors.WHITE, IndexedColors.BROWN, false);
            CellStyle cabecera = estilo(libro, true, IndexedColors.WHITE, IndexedColors.BROWN, false);
            CellStyle texto = estilo(libro, false, IndexedColors.BLACK, IndexedColors.WHITE, false);
            CellStyle alterno = estilo(libro, false, IndexedColors.BLACK, IndexedColors.GREY_25_PERCENT, false);
            CellStyle numero = estilo(libro, false, IndexedColors.BLACK, IndexedColors.WHITE, true);
            CellStyle numeroAlterno = estilo(libro, false, IndexedColors.BLACK, IndexedColors.GREY_25_PERCENT, true);
            CellStyle agotado = estilo(libro, true, IndexedColors.DARK_RED, IndexedColors.ROSE, false);
            CellStyle bajo = estilo(libro, true, IndexedColors.BROWN, IndexedColors.LIGHT_YELLOW, false);

            filaCompleta(hoja, 0, "INVENTARIO POR SEDE", titulo);
            hoja.getRow(0).setHeightInPoints(30);
            filaCompleta(hoja, 1, empresa + " · " + sede, texto);
            filaCompleta(hoja, 2, "Generado: " + fecha.format(FECHA) + " (America/Lima)", texto);
            String estado = activo == null ? "Todos" : activo ? "Activos" : "Inactivos";
            filaCompleta(hoja, 3, "Estado: " + estado + " · Búsqueda: "
                    + (buscar == null || buscar.isBlank() ? "Sin filtro" : buscar.trim()), texto);
            filaCompleta(hoja, 4, "Productos incluidos: " + filas.size(), texto);
            Row encabezados = hoja.createRow(FILA_CABECERA);
            encabezados.setHeightInPoints(25);
            for (int c = 0; c < COLUMNAS.length; c++) {
                Cell celda = encabezados.createCell(c);
                celda.setCellValue(COLUMNAS[c]);
                celda.setCellStyle(cabecera);
            }
            for (int n = 0; n < filas.size(); n++) {
                InventarioVista i = filas.get(n);
                Row fila = hoja.createRow(FILA_CABECERA + 1 + n);
                fila.setHeightInPoints(30);
                String[] valores = {i.codigo(), i.nombre(), i.categoria(), i.sede(), i.unidad(), "", "",
                        Boolean.TRUE.equals(i.activo()) ? "Activo" : "Inactivo", i.getSituacion()};
                for (int c = 0; c < valores.length; c++) {
                    Cell celda = fila.createCell(c);
                    celda.setCellValue(valores[c]);
                    celda.setCellStyle(n % 2 == 0 ? texto : alterno);
                }
                fila.getCell(5).setCellValue(i.stock().doubleValue());
                fila.getCell(6).setCellValue(i.stockMinimo().doubleValue());
                fila.getCell(5).setCellStyle(n % 2 == 0 ? numero : numeroAlterno);
                fila.getCell(6).setCellStyle(n % 2 == 0 ? numero : numeroAlterno);
                if (Boolean.TRUE.equals(i.activo())) {
                    if (i.stock().signum() == 0) fila.getCell(8).setCellStyle(agotado);
                    else if (i.isStockBajo()) fila.getCell(8).setCellStyle(bajo);
                }
            }
            if (filas.isEmpty()) filaCompleta(hoja, FILA_CABECERA + 1,
                    "No hay productos que coincidan con los filtros seleccionados.", texto);
            int[] anchos = {19, 40, 24, 23, 11, 16, 16, 13, 18};
            for (int c = 0; c < anchos.length; c++) hoja.setColumnWidth(c, anchos[c] * 256);
            hoja.createFreezePane(0, FILA_CABECERA + 1);
            hoja.setAutoFilter(new CellRangeAddress(FILA_CABECERA, FILA_CABECERA + filas.size(), 0, COLUMNAS.length - 1));
            hoja.setRepeatingRows(new CellRangeAddress(FILA_CABECERA, FILA_CABECERA, -1, -1));
            hoja.getPrintSetup().setLandscape(true);
            hoja.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
            hoja.getPrintSetup().setFitWidth((short) 1);
            hoja.getPrintSetup().setFitHeight((short) 0);
            hoja.setFitToPage(true);
            libro.write(salida);
            return salida.toByteArray();
        }
    }

    private CellStyle estilo(Workbook libro, boolean negrita, IndexedColors color, IndexedColors fondo, boolean numerico) {
        CellStyle estilo = libro.createCellStyle();
        Font fuente = libro.createFont();
        fuente.setFontName("Arial");
        fuente.setFontHeightInPoints((short) 11);
        fuente.setBold(negrita);
        fuente.setColor(color.getIndex());
        estilo.setFont(fuente);
        estilo.setFillForegroundColor(fondo.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        estilo.setVerticalAlignment(VerticalAlignment.CENTER);
        estilo.setWrapText(true);
        if (numerico) {
            estilo.setDataFormat(libro.createDataFormat().getFormat("0.####"));
            estilo.setAlignment(HorizontalAlignment.RIGHT);
        }
        return estilo;
    }

    private void filaCompleta(Sheet hoja, int indice, String valor, CellStyle estilo) {
        Row fila = hoja.createRow(indice);
        fila.setHeightInPoints(23);
        for (int c = 0; c < COLUMNAS.length; c++) fila.createCell(c).setCellStyle(estilo);
        fila.getCell(0).setCellValue(valor);
        hoja.addMergedRegion(new CellRangeAddress(indice, indice, 0, COLUMNAS.length - 1));
    }

    public record Archivo(byte[] contenido, String nombre) {}
}
