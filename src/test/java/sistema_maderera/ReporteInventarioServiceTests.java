package sistema_maderera;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import sistema_maderera.dto.InventarioVista;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.service.InventarioService;
import sistema_maderera.service.ReporteInventarioService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReporteInventarioServiceTests {
    private final InventarioService inventario = mock(InventarioService.class);
    private final ReporteInventarioService reportes = new ReporteInventarioService(inventario,
            Clock.fixed(Instant.parse("2026-10-15T15:30:00Z"), ZoneId.of("America/Lima")));
    private final Logger logger = (Logger) LoggerFactory.getLogger(ReporteInventarioService.class);
    private final ListAppender<ILoggingEvent> eventos = new ListAppender<>();

    @BeforeEach
    void preparar() {
        eventos.start();
        logger.addAppender(eventos);
        when(inventario.listar(7L)).thenReturn(List.of(
                fila(1, "Madera de tornillo", "Maderas", "12.3456", "15", true),
                fila(2, "=2+2", "Planchas", "0", "4", false),
                fila(3, "Triplay", "Planchas", "0", "2", true)));
    }

    @AfterEach
    void cerrarRegistro() {
        logger.detachAppender(eventos);
        eventos.stop();
    }

    private InventarioVista fila(long id, String nombre, String categoria, String stock, String minimo, boolean activo) {
        return new InventarioVista(id, "PRO-" + id, nombre, categoria, "Sede Melgar", "P²",
                new BigDecimal(stock), new BigDecimal(minimo), activo);
    }

    @Test
    void generaExcelLegibleConDecimalesFiltrosYTextosSinFormulas() throws Exception {
        var archivo = reportes.generar(7, 12, "COMAPAR", "Sede Melgar", "", null);
        assertThat(archivo.nombre()).isEqualTo("inventario-sede-7-20261015-103000.xlsx");
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(archivo.contenido()))) {
            var hoja = libro.getSheet("Inventario");
            assertThat(hoja.getRow(1).getCell(0).getStringCellValue()).contains("COMAPAR", "Sede Melgar");
            assertThat(hoja.getRow(2).getCell(0).getStringCellValue()).contains("15/10/2026 10:30");
            assertThat(hoja.getRow(4).getCell(0).getStringCellValue()).contains("3");
            assertThat(hoja.getRow(6).getCell(6).getStringCellValue()).isEqualTo("Stock mínimo");
            assertThat(hoja.getRow(7).getCell(5).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(hoja.getRow(7).getCell(5).getNumericCellValue()).isEqualTo(12.3456);
            assertThat(hoja.getRow(7).getCell(8).getStringCellValue()).isEqualTo("Stock bajo");
            assertThat(hoja.getRow(8).getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(hoja.getRow(8).getCell(1).getStringCellValue()).isEqualTo("=2+2");
            assertThat(hoja.getRow(8).getCell(8).getStringCellValue()).isEqualTo("Inactivo");
            assertThat(hoja.getRow(9).getCell(8).getStringCellValue()).isEqualTo("Sin stock");
            assertThat(hoja.getPaneInformation().isFreezePane()).isTrue();
            assertThat(hoja.getCTWorksheet().getAutoFilter().getRef()).isEqualTo("A7:I10");
        }
        assertThat(eventos.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.INFO);
            assertThat(e.getFormattedMessage()).contains("usuarioId=12", "sedeId=7", "registros=3");
        });
    }

    @Test
    void respetaBusquedaSinTildesYEstadoDelProducto() throws Exception {
        var archivo = reportes.generar(7, 12, "COMAPAR", "Sede Melgar", "  PLANCHÁS  ", true);
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(archivo.contenido()))) {
            var hoja = libro.getSheetAt(0);
            assertThat(hoja.getRow(3).getCell(0).getStringCellValue()).contains("Activos", "PLANCHÁS");
            assertThat(hoja.getRow(7).getCell(1).getStringCellValue()).isEqualTo("Triplay");
            assertThat(hoja.getLastRowNum()).isEqualTo(7);
        }
    }

    @Test
    void sinCoincidenciasGeneraArchivoValidoConCabeceras() throws Exception {
        var archivo = reportes.generar(7, 12, "COMAPAR", "Sede Melgar", "no existe", false);
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(archivo.contenido()))) {
            var hoja = libro.getSheetAt(0);
            assertThat(hoja.getRow(4).getCell(0).getStringCellValue()).endsWith("0");
            assertThat(hoja.getRow(7).getCell(0).getStringCellValue()).contains("No hay productos");
        }
    }

    @Test
    void stockEnMinimo() throws Exception {
        when(inventario.listar(7L)).thenReturn(List.of(
                fila(1, "Melamina", "Planchas", "10", "10", true)));

        var archivo = reportes.generar(7, 12, "COMAPAR", "Sede Melgar", "", null);
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(archivo.contenido()))) {
            var situacion = libro.getSheet("Inventario").getRow(7).getCell(8);
            assertThat(situacion.getStringCellValue()).isEqualTo("Stock bajo");
            assertThat(situacion.getCellStyle().getFillForegroundColor())
                    .isEqualTo(IndexedColors.LIGHT_YELLOW.getIndex());
        }
    }

    @Test
    void registraErrorSinAnunciarReporteGenerado() {
        when(inventario.listar(7L)).thenThrow(new IllegalStateException("Fallo de lectura"));
        assertThatThrownBy(() -> reportes.generar(7, 12, "COMAPAR", "Sede Melgar", "", null))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("No se pudo generar");
        assertThat(eventos.list).noneMatch(e -> e.getLevel().equals(Level.INFO));
        assertThat(eventos.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(Level.ERROR);
            assertThat(e.getFormattedMessage()).contains("usuarioId=12", "sedeId=7");
        });
    }
}
