package sistema_maderera;

import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import sistema_maderera.dto.*;
import sistema_maderera.entity.*;
import sistema_maderera.exception.RecursoNoEncontradoException;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.*;
import sistema_maderera.service.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventarioIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired SedeRepository sedes;
    @Autowired EmpresaRepository empresas;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired ProductoRepository productos;
    @Autowired InventarioRepository inventarios;
    @Autowired MovimientoInventarioRepository movimientos;
    @Autowired TransferenciaRepository transferencias;
    @Autowired TransferenciaService transferenciaService;
    @Autowired CategoriaService categoriaService;
    @Autowired ProductoService productoService;
    @Autowired InventarioService inventario;
    @Autowired DemoDataService demo;
    @Autowired PasswordEncoder encoder;
    private Long parconaId;
    private Long icaId;
    private Long adminId;
    private Long melaminaId;
    private Long melaminaParconaId;
    private Long melaminaIcaId;
    private Long maderasId;
    private Long planchasId;

    @BeforeEach
    void preparar() {
        limpiar();
        demo.inicializar("ClaveDePrueba1");
        Usuario admin = usuarios.findByUsuario("admin").orElseThrow();
        adminId = admin.getId();
        parconaId = usuarios.findByUsuario("consulta").orElseThrow().getSede().getId();
        icaId = usuarios.findByUsuario("admin.ica").orElseThrow().getSede().getId();
        melaminaId = productos.findAll().stream().filter(p -> p.getNombre().contains("melamina")).findFirst().orElseThrow().getId();
        melaminaParconaId = item(parconaId, melaminaId);
        melaminaIcaId = item(icaId, melaminaId);
        maderasId = categorias.findByCodigoIgnoreCase("CAT-MAD").orElseThrow().getId();
        planchasId = categorias.findByCodigoIgnoreCase("CAT-PLA").orElseThrow().getId();
    }

    private void limpiar() {
        transferencias.deleteAll();
        movimientos.deleteAll();
        inventarios.deleteAll();
        productos.deleteAll();
        categorias.deleteAll();
        usuarios.deleteAll();
        sedes.deleteAll();
        empresas.deleteAll();
    }

    private Long item(Long sedeId, Long productoId) {
        return inventarios.listarPorSede(sedeId).stream().filter(i -> i.getProducto().getId().equals(productoId))
                .findFirst().orElseThrow().getId();
    }

    private BigDecimal stock(Long inventarioId) {
        return inventarios.findById(inventarioId).orElseThrow().getStock();
    }

    private MovimientoForm movimiento(Long id, TipoMovimiento tipo, String cantidad) {
        MovimientoForm form = new MovimientoForm();
        form.setInventarioId(id);
        form.setTipo(tipo);
        form.setCantidad(new BigDecimal(cantidad));
        form.setMotivo("Prueba de inventario");
        return form;
    }

    private TransferenciaForm transferencia(Long inventarioOrigenId, Long sedeDestinoId, String cantidad) {
        TransferenciaForm form = new TransferenciaForm();
        form.setInventarioOrigenId(inventarioOrigenId);
        form.setSedeDestinoId(sedeDestinoId);
        form.setCantidad(new BigDecimal(cantidad));
        form.setMotivo("Reposición entre sedes");
        return form;
    }

    private ProductoForm producto(String nombre, Long categoriaId) {
        ProductoForm form = new ProductoForm();
        form.setNombre(nombre);
        form.setCategoriaId(categoriaId);
        form.setUnidadMedida("UND");
        form.setPrecioCompra(new BigDecimal("25.50"));
        form.setPrecioVenta(new BigDecimal("30"));
        return form;
    }

    private MockHttpSession login(String usuario) throws Exception {
        MockHttpSession sesion = (MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("usuario", usuario).param("password", "ClaveDePrueba1"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        if ("admin".equals(usuario)) {
            mvc.perform(post("/sede-trabajo").session(sesion).with(csrf())
                    .param("sedeId", parconaId.toString())).andExpect(redirectedUrl("/dashboard"));
        }
        return sesion;
    }

    @Test
    void exportacionExcelRespetaSedeFiltrosYNoModificaExistencias() throws Exception {
        BigDecimal anterior = stock(melaminaParconaId);
        long movimientosAntes = movimientos.count();
        var respuesta = mvc.perform(get("/inventario/exportar").session(login("consulta"))
                .param("buscar", "melamina").param("activo", "true").param("sedeId", icaId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", containsString("attachment;")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse();
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(respuesta.getContentAsByteArray()))) {
            var hoja = libro.getSheetAt(0);
            assertThat(hoja.getRow(1).getCell(0).getStringCellValue()).contains("Sede Parcona");
            assertThat(hoja.getRow(7).getCell(5).getNumericCellValue()).isEqualTo(20);
            assertThat(hoja.getLastRowNum()).isEqualTo(7);
        }
        assertThat(stock(melaminaParconaId)).isEqualByComparingTo(anterior);
        assertThat(movimientos.count()).isEqualTo(movimientosAntes);
        mvc.perform(get("/inventario").session(login("admin"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Exportar Excel")));
        var local = mvc.perform(get("/inventario/exportar").session(login("admin.ica")))
                .andExpect(status().isOk()).andReturn().getResponse();
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(local.getContentAsByteArray()))) {
            assertThat(libro.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).contains("Sede Ica");
        }
    }

    @Test
    void exportacionRequiereLoginYSedeSeleccionada() throws Exception {
        mvc.perform(get("/inventario/exportar")).andExpect(status().is3xxRedirection());
        var sesion = (MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("usuario", "admin").param("password", "ClaveDePrueba1"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        mvc.perform(get("/inventario/exportar").session(sesion))
                .andExpect(status().isSeeOther()).andExpect(redirectedUrl("/seleccionar-sede"));
    }

    @Test
    void demostracionCreaInventariosIndependientesYPasswordsProtegidos() {
        assertThat(sedes.count()).isEqualTo(2);
        assertThat(empresas.count()).isEqualTo(1);
        assertThat(usuarios.count()).isEqualTo(3);
        assertThat(productos.count()).isEqualTo(3);
        assertThat(inventarios.count()).isEqualTo(5);
        assertThat(movimientos.count()).isEqualTo(4);
        assertThat(encoder.matches("ClaveDePrueba1", usuarios.findById(adminId).orElseThrow().getPassword())).isTrue();
        var parcona = inventario.resumen(parconaId);
        var ica = inventario.resumen(icaId);
        assertThat(parcona.productos()).isEqualTo(3);
        assertThat(parcona.sinStock()).isEqualTo(1);
        assertThat(parcona.stockBajo()).isEqualTo(2);
        assertThat(parcona.valor()).isEqualByComparingTo("2424");
        assertThat(ica.productos()).isEqualTo(2);
        assertThat(ica.stockBajo()).isZero();
        assertThat(ica.valor()).isEqualByComparingTo("1240");
    }

    @Test
    void dashboardReflejaMovimientosSoloEnLaSedeSeleccionada() throws Exception {
        MockHttpSession sesion = login("admin");
        var paginaParcona = mvc.perform(get("/dashboard").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sede Parcona")))
                .andExpect(content().string(containsString("Plancha de triplay")))
                .andReturn();
        DashboardResumen parconaAntes = (DashboardResumen) paginaParcona.getModelAndView().getModel().get("resumen");
        assertThat(parconaAntes.productos()).isEqualTo(3);
        assertThat(parconaAntes.sinStock()).isEqualTo(1);
        assertThat(parconaAntes.stockBajo()).isEqualTo(2);
        assertThat(parconaAntes.valor()).isEqualByComparingTo("2424");

        MovimientoForm entrada = movimiento(melaminaParconaId, TipoMovimiento.ENTRADA, "2");
        entrada.setMotivo("Entrada de prueba dashboard");
        inventario.mover(parconaId, adminId, entrada);
        var paginaActualizada = mvc.perform(get("/dashboard").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Entrada de prueba dashboard")))
                .andReturn();
        DashboardResumen parconaDespues = (DashboardResumen) paginaActualizada.getModelAndView().getModel().get("resumen");
        assertThat(parconaDespues.valor()).isEqualByComparingTo("2664");
        assertThat(parconaDespues.movimientos().getFirst().motivo()).isEqualTo("Entrada de prueba dashboard");

        StockMinimoForm minimo = new StockMinimoForm();
        minimo.setStockMinimo(new BigDecimal("25"));
        inventario.cambiarMinimo(parconaId, melaminaParconaId, minimo);
        var paginaConAlerta = mvc.perform(get("/dashboard").session(sesion))
                .andExpect(status().isOk()).andReturn();
        DashboardResumen parconaConAlerta = (DashboardResumen) paginaConAlerta.getModelAndView().getModel().get("resumen");
        assertThat(parconaConAlerta.stockBajo()).isEqualTo(3);

        mvc.perform(post("/sede-trabajo").session(sesion).with(csrf())
                .param("sedeId", icaId.toString())).andExpect(redirectedUrl("/dashboard"));
        var paginaIca = mvc.perform(get("/dashboard").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sede Ica")))
                .andExpect(content().string(not(containsString("Entrada de prueba dashboard"))))
                .andExpect(content().string(not(containsString("Plancha de triplay"))))
                .andReturn();
        DashboardResumen ica = (DashboardResumen) paginaIca.getModelAndView().getModel().get("resumen");
        assertThat(ica.productos()).isEqualTo(2);
        assertThat(ica.stockBajo()).isZero();
        assertThat(ica.valor()).isEqualByComparingTo("1240");
    }

    @Test
    void reiniciarDemostracionNoReemplazaDatosNiPasswords() {
        inventario.mover(parconaId, adminId, movimiento(melaminaParconaId, TipoMovimiento.ENTRADA, "2"));
        String hash = usuarios.findById(adminId).orElseThrow().getPassword();
        demo.inicializar("OtraClaveDePrueba");
        assertThat(stock(melaminaParconaId)).isEqualByComparingTo("22");
        assertThat(usuarios.findById(adminId).orElseThrow().getPassword()).isEqualTo(hash);
        assertThat(inventarios.count()).isEqualTo(5);
    }

    @Test
    void demostracionSinClaveValidaFallaAntesDeCrearDatos() {
        limpiar();
        assertThatThrownBy(() -> demo.inicializar("")).isInstanceOf(IllegalStateException.class);
        assertThat(sedes.count()).isZero();
        assertThat(usuarios.count()).isZero();
    }

    @Test
    void categoriasNormalizanDatosYRechazanDuplicados() {
        CategoriaForm form = new CategoriaForm();
        form.setCodigo("CODIGO-FALSIFICADO");
        form.setNombre("  Nueva   categoría  ");
        Categoria creada = categoriaService.guardar(form);
        assertThat(creada.getCodigo()).isEqualTo("CAT-NUE");
        assertThat(creada.getNombre()).isEqualTo("NUEVA CATEGORÍA");
        assertThatThrownBy(() -> categoriaService.guardar(form)).isInstanceOf(ReglaNegocioException.class);
        form.setNombre("nueva categoría");
        assertThatThrownBy(() -> categoriaService.guardar(form)).isInstanceOf(ReglaNegocioException.class);
        form.setNombre("Nuevos productos");
        Categoria segunda = categoriaService.guardar(form);
        assertThat(segunda.getCodigo()).isEqualTo("CAT-NUE-2");
        CategoriaForm editar = categoriaService.formulario(creada.getId());
        editar.setNombre("Herramientas");
        editar.setCodigo("CAMBIADO");
        Categoria editada = categoriaService.guardar(editar);
        assertThat(editada.getCodigo()).isEqualTo("CAT-NUE");
        assertThat(editada.getNombre()).isEqualTo("HERRAMIENTAS");
        assertThat(categorias.count()).isEqualTo(4);
    }

    @Test
    void formularioDeCategoriaGeneraCodigoSinAceptarUnoManual() throws Exception {
        MockHttpSession sesion = login("admin");
        mvc.perform(get("/categorias/nueva").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"codigoCategoria\"")))
                .andExpect(content().string(containsString("disabled")))
                .andExpect(content().string(containsString("categoria-form.js")));
        mvc.perform(post("/categorias/guardar").session(sesion).with(csrf())
                .param("nombre", "ferreteria").param("codigo", "CODIGO-MANUAL"))
                .andExpect(redirectedUrl("/categorias"));
        Categoria guardada = categorias.findByNombreIgnoreCase("FERRETERIA").orElseThrow();
        assertThat(guardada.getCodigo()).isEqualTo("CAT-FER");
        assertThat(guardada.getNombre()).isEqualTo("FERRETERIA");
    }

    @Test
    void codigosDeProductosSonGlobalesYSeConservanAlEditar() {
        Producto primero = productoService.guardar(producto("Producto A", maderasId));
        Producto segundo = productoService.guardar(producto("Producto B", planchasId));
        assertThat(primero.getCodigo()).matches("PRO-\\d{6,}");
        assertThat(segundo.getCodigo()).isNotEqualTo(primero.getCodigo());
        ProductoForm editar = productoService.formulario(primero.getId());
        editar.setCodigo("CODIGO-FALSIFICADO");
        editar.setCategoriaId(planchasId);
        assertThat(productoService.guardar(editar).getCodigo()).isEqualTo(primero.getCodigo());
        assertThat(primero.getEstado()).isTrue();
        assertThat(inventarios.count()).isEqualTo(5);
    }

    @Test
    void productoActivoPuedeTenerStockCeroYNoCambiarUnidad() {
        ProductoForm form = productoService.formulario(melaminaId);
        form.setUnidadMedida("KG");
        assertThatThrownBy(() -> productoService.guardar(form)).isInstanceOf(ReglaNegocioException.class);
        inventario.mover(parconaId, adminId, movimiento(melaminaParconaId, TipoMovimiento.AJUSTE, "0"));
        assertThat(productoService.buscar(melaminaId).getEstado()).isTrue();
        assertThat(stock(melaminaIcaId)).isEqualByComparingTo("8");
    }

    @Test
    void categoriaInactivaPermiteEditarSusProductosPeroNoAsignarNuevos() {
        categoriaService.cambiarEstado(planchasId);
        ProductoForm existente = productoService.formulario(melaminaId);
        existente.setNombre("Melamina editada");
        assertThat(productoService.guardar(existente).getNombre()).isEqualTo("Melamina editada");
        assertThatThrownBy(() -> productoService.guardar(producto("Nuevo", planchasId)))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void validaPreciosUnidadesYPrecisionAntesDePersistir() {
        ProductoForm form = producto("Producto inválido", maderasId);
        form.setPrecioCompra(new BigDecimal("-1"));
        assertThatThrownBy(() -> productoService.guardar(form)).isInstanceOf(ReglaNegocioException.class);
        form.setPrecioCompra(new BigDecimal("1.001"));
        assertThatThrownBy(() -> productoService.guardar(form)).isInstanceOf(ReglaNegocioException.class);
        form.setPrecioCompra(BigDecimal.ONE);
        form.setUnidadMedida("INVALIDA");
        assertThatThrownBy(() -> productoService.guardar(form)).isInstanceOf(ReglaNegocioException.class);
        assertThat(productos.count()).isEqualTo(3);
    }

    @Test
    void registrarProductoSoloAgregaStockCeroEnLaSedeActual() {
        Long triplay = productos.findAll().stream().filter(p -> p.getNombre().contains("triplay")).findFirst().orElseThrow().getId();
        RegistroInventarioForm form = new RegistroInventarioForm();
        form.setProductoId(triplay);
        form.setStockMinimo(new BigDecimal("6"));
        Inventario nuevo = inventario.registrar(icaId, form);
        assertThat(nuevo.getStock()).isEqualByComparingTo("0");
        assertThat(inventario.resumen(parconaId).productos()).isEqualTo(3);
        assertThat(inventario.resumen(icaId).productos()).isEqualTo(3);
        assertThatThrownBy(() -> inventario.registrar(icaId, form)).isInstanceOf(ReglaNegocioException.class);
        assertThat(inventarios.count()).isEqualTo(6);
    }

    @Test
    void productoNuevoRecibeStockSoloEnSedeActualYSePuedeEditarEnOtra() throws Exception {
        MockHttpSession sesion = login("admin");
        mvc.perform(get("/productos/nuevo").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Stock actual")))
                .andExpect(content().string(containsString("Sede Parcona")));
        mvc.perform(post("/productos/guardar").session(sesion).with(csrf())
                .param("nombre", "Tablón nuevo").param("categoriaId", maderasId.toString())
                .param("unidadMedida", "UND").param("precioCompra", "10")
                .param("precioVenta", "15").param("stockInicial", "35")
                .param("stockMinimo", "5").param("sedeId", icaId.toString()))
                .andExpect(redirectedUrl("/productos"));
        Producto nuevo = productos.findAll().stream().filter(p -> p.getNombre().equals("Tablón nuevo"))
                .findFirst().orElseThrow();
        Inventario parcona = inventarios.listarPorSede(parconaId).stream()
                .filter(i -> i.getProducto().getId().equals(nuevo.getId())).findFirst().orElseThrow();
        assertThat(parcona.getStock()).isEqualByComparingTo("35");
        assertThat(parcona.getStockMinimo()).isEqualByComparingTo("5");
        assertThat(inventario.listar(icaId)).noneMatch(i -> i.codigo().equals(nuevo.getCodigo()));
        assertThat(inventario.recientes(parconaId).getFirst().motivo()).contains("Stock inicial");

        mvc.perform(post("/sede-trabajo").session(sesion).with(csrf())
                .param("sedeId", icaId.toString())).andExpect(redirectedUrl("/dashboard"));
        mvc.perform(get("/productos").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Agregar producto existente"))));
        mvc.perform(get("/productos/editar/" + nuevo.getId()).session(sesion))
                .andExpect(status().isOk()).andExpect(model().attribute("inventarioRegistrado", false))
                .andExpect(content().string(containsString("Sede Ica")))
                .andExpect(content().string(containsString("name=\"stockInicial\" value=\"0\"")));
        mvc.perform(post("/productos/guardar").session(sesion).with(csrf())
                .param("id", nuevo.getId().toString()).param("nombre", nuevo.getNombre())
                .param("categoriaId", maderasId.toString()).param("unidadMedida", "UND")
                .param("precioCompra", "10").param("precioVenta", "15").param("estado", "true")
                .param("stockInicial", "6").param("stockMinimo", "3")
                .param("sedeId", parconaId.toString()))
                .andExpect(redirectedUrl("/productos"));
        Inventario ica = inventarios.listarPorSede(icaId).stream()
                .filter(i -> i.getProducto().getId().equals(nuevo.getId())).findFirst().orElseThrow();
        assertThat(ica.getStock()).isEqualByComparingTo("6");
        assertThat(ica.getStockMinimo()).isEqualByComparingTo("3");
        assertThat(stock(parcona.getId())).isEqualByComparingTo("35");
        assertThat(productos.count()).isEqualTo(4);
        assertThat(movimientos.count()).isEqualTo(6);
    }

    @Test
    void catalogoIndicaSedesRegistradasAunqueElStockSeaCero() throws Exception {
        Producto sinSede = productoService.guardar(producto("Producto pendiente", maderasId));
        MockHttpSession sesion = login("admin");
        mvc.perform(get("/productos").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sedes registradas")))
                .andExpect(content().string(containsString("Sede Ica, Sede Parcona")))
                .andExpect(content().string(containsString("Producto pendiente")))
                .andExpect(content().string(containsString("Sin sedes")))
                .andExpect(content().string(not(containsString("<th>Estado</th>"))));
        productoService.cambiarEstado(sinSede.getId());
        mvc.perform(get("/productos").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"badge inactive product-status\">Inactivo</span>")));
    }

    @Test
    void editarProductoAntiguoRegistraInventarioYAjustaSinDuplicarMovimientos() throws Exception {
        Producto clavo = productoService.guardar(producto("Clavo de 1 pulgada", maderasId));
        MockHttpSession sesion = login("admin");
        mvc.perform(get("/productos/editar/" + clavo.getId()).session(sesion))
                .andExpect(status().isOk()).andExpect(model().attribute("inventarioRegistrado", false))
                .andExpect(content().string(containsString("Stock actual")))
                .andExpect(content().string(containsString("Stock mínimo")));
        mvc.perform(post("/productos/guardar").session(sesion).with(csrf())
                .param("id", clavo.getId().toString()).param("nombre", clavo.getNombre())
                .param("categoriaId", maderasId.toString()).param("unidadMedida", "UND")
                .param("precioCompra", "25.50").param("precioVenta", "30").param("estado", "true")
                .param("stockInicial", "35").param("stockMinimo", "5"))
                .andExpect(redirectedUrl("/productos"));
        Inventario creado = inventarios.findByProductoIdAndSedeId(clavo.getId(), parconaId).orElseThrow();
        assertThat(creado.getStock()).isEqualByComparingTo("35");
        assertThat(creado.getStockMinimo()).isEqualByComparingTo("5");
        assertThat(inventarios.findByProductoIdAndSedeId(clavo.getId(), icaId)).isEmpty();
        mvc.perform(get("/productos/editar/" + clavo.getId()).session(sesion))
                .andExpect(status().isOk()).andExpect(model().attribute("inventarioRegistrado", true))
                .andExpect(content().string(containsString("name=\"stockInicial\" value=\"35\"")))
                .andExpect(content().string(containsString("name=\"stockMinimo\" value=\"5\"")));
        long totalMovimientos = movimientos.count();
        Long version = creado.getVersion();

        mvc.perform(post("/productos/guardar").session(sesion).with(csrf())
                .param("id", clavo.getId().toString()).param("nombre", clavo.getNombre())
                .param("categoriaId", maderasId.toString()).param("unidadMedida", "UND")
                .param("precioCompra", "25.50").param("precioVenta", "30").param("estado", "true")
                .param("stockInicial", "35").param("stockMinimo", "5")
                .param("inventarioVersion", version.toString()))
                .andExpect(redirectedUrl("/productos"));
        assertThat(movimientos.count()).isEqualTo(totalMovimientos);

        mvc.perform(post("/productos/guardar").session(sesion).with(csrf())
                .param("id", clavo.getId().toString()).param("nombre", clavo.getNombre())
                .param("categoriaId", maderasId.toString()).param("unidadMedida", "UND")
                .param("precioCompra", "25.50").param("precioVenta", "30").param("estado", "true")
                .param("stockInicial", "40").param("stockMinimo", "7")
                .param("inventarioVersion", version.toString()))
                .andExpect(redirectedUrl("/productos"));
        Inventario actualizado = inventarios.findById(creado.getId()).orElseThrow();
        assertThat(actualizado.getStock()).isEqualByComparingTo("40");
        assertThat(actualizado.getStockMinimo()).isEqualByComparingTo("7");
        assertThat(movimientos.count()).isEqualTo(totalMovimientos + 1);
        assertThat(inventario.recientes(parconaId).getFirst().tipo()).isEqualTo(TipoMovimiento.AJUSTE);

        mvc.perform(post("/productos/guardar").session(sesion).with(csrf())
                .param("id", clavo.getId().toString()).param("nombre", "Nombre que no debe guardarse")
                .param("categoriaId", maderasId.toString()).param("unidadMedida", "UND")
                .param("precioCompra", "25.50").param("precioVenta", "30").param("estado", "true")
                .param("stockInicial", "44").param("stockMinimo", "7")
                .param("inventarioVersion", version.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("El inventario cambió mientras editabas")));
        assertThat(stock(creado.getId())).isEqualByComparingTo("40");
        assertThat(productoService.buscar(clavo.getId()).getNombre()).isEqualTo(clavo.getNombre());
    }

    @Test
    void altaConStockFraccionarioEnUnidadesNoDejaProductoIncompleto() throws Exception {
        MockHttpSession sesion = login("admin");
        mvc.perform(post("/productos/guardar").session(sesion).with(csrf())
                .param("nombre", "Unidad inválida").param("categoriaId", maderasId.toString())
                .param("unidadMedida", "UND").param("precioCompra", "10")
                .param("precioVenta", "15").param("stockInicial", "1.5")
                .param("stockMinimo", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("cantidades enteras")));
        assertThat(productos.count()).isEqualTo(3);
        assertThat(inventarios.count()).isEqualTo(5);
        assertThat(movimientos.count()).isEqualTo(4);
    }

    @Test
    void entradaSalidaYAjusteRegistranCambioAutorYSaldo() {
        var entrada = inventario.mover(parconaId, adminId, movimiento(melaminaParconaId, TipoMovimiento.ENTRADA, "5"));
        assertThat(entrada.getStockAnterior()).isEqualByComparingTo("20");
        assertThat(entrada.getStockPosterior()).isEqualByComparingTo("25");
        var salida = inventario.mover(parconaId, adminId, movimiento(melaminaParconaId, TipoMovimiento.SALIDA, "3"));
        assertThat(salida.getCantidad()).isEqualByComparingTo("-3");
        var ajuste = inventario.mover(parconaId, adminId, movimiento(melaminaParconaId, TipoMovimiento.AJUSTE, "7"));
        assertThat(ajuste.getCantidad()).isEqualByComparingTo("-15");
        assertThat(stock(melaminaParconaId)).isEqualByComparingTo("7");
        assertThat(stock(melaminaIcaId)).isEqualByComparingTo("8");
        assertThat(inventario.recientes(parconaId).getFirst().usuario()).isEqualTo("Administrador general");
        assertThat(inventario.resumen(icaId).valor()).isEqualByComparingTo("1240");
    }

    @Test
    void salidaSinStockSeRevierteSinCrearMovimiento() {
        long anteriores = movimientos.count();
        assertThatThrownBy(() -> inventario.mover(parconaId, adminId,
                movimiento(melaminaParconaId, TipoMovimiento.SALIDA, "21"))).isInstanceOf(ReglaNegocioException.class);
        assertThat(stock(melaminaParconaId)).isEqualByComparingTo("20");
        assertThat(movimientos.count()).isEqualTo(anteriores);
    }

    @Test
    void rechazaCeroFraccionesDeUnidadesYAjustesSinCambio() {
        long anteriores = movimientos.count();
        for (MovimientoForm form : new MovimientoForm[]{
                movimiento(melaminaParconaId, TipoMovimiento.ENTRADA, "0"),
                movimiento(melaminaParconaId, TipoMovimiento.ENTRADA, "0.5"),
                movimiento(melaminaParconaId, TipoMovimiento.AJUSTE, "20"),
                movimiento(melaminaParconaId, TipoMovimiento.SALIDA, "-1")}) {
            assertThatThrownBy(() -> inventario.mover(parconaId, adminId, form)).isInstanceOf(ReglaNegocioException.class);
        }
        assertThat(movimientos.count()).isEqualTo(anteriores);
        assertThat(stock(melaminaParconaId)).isEqualByComparingTo("20");
    }

    @Test
    void productosMedidosEnPiesPermitenFracciones() {
        Long tornillo = productos.findAll().stream().filter(p -> p.getNombre().contains("tornillo")).findFirst().orElseThrow().getId();
        Long id = item(parconaId, tornillo);
        inventario.mover(parconaId, adminId, movimiento(id, TipoMovimiento.ENTRADA, "0.125"));
        assertThat(stock(id)).isEqualByComparingTo("3.125");
    }

    @Test
    void stockSeMuestraSinCerosSobrantesYRechazaMasDeCuatroDecimales() throws Exception {
        Long tornillo = productos.findAll().stream().filter(p -> p.getNombre().contains("tornillo"))
                .findFirst().orElseThrow().getId();
        Long id = item(parconaId, tornillo);
        inventario.mover(parconaId, adminId, movimiento(id, TipoMovimiento.ENTRADA, "0.1250"));
        StockMinimoForm minimo = new StockMinimoForm();
        minimo.setStockMinimo(new BigDecimal("4.5000"));
        inventario.cambiarMinimo(parconaId, id, minimo);
        MockHttpSession sesion = login("admin");

        mvc.perform(get("/inventario").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"number\">20</td>")))
                .andExpect(content().string(containsString("class=\"number\">3.125</td>")))
                .andExpect(content().string(containsString("Sede Parcona")))
                .andExpect(content().string(not(containsString("Stock mínimo"))))
                .andExpect(content().string(not(containsString("20.0000"))));
        mvc.perform(get("/movimientos").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"number\">0.125</td>")));
        mvc.perform(get("/dashboard").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"number\">3.125</td>")))
                .andExpect(content().string(containsString("Stock bajo")))
                .andExpect(content().string(not(containsString("3.1250"))));

        assertThatThrownBy(() -> inventario.mover(parconaId, adminId,
                movimiento(id, TipoMovimiento.ENTRADA, "0.00001")))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("4 decimales");
        assertThat(stock(id)).isEqualByComparingTo("3.125");
    }

    @Test
    void inventarioYAutorDeOtraSedeNoPuedenOperar() {
        Long usuarioParcona = usuarios.findByUsuario("consulta").orElseThrow().getId();
        assertThatThrownBy(() -> inventario.mover(parconaId, adminId,
                movimiento(melaminaIcaId, TipoMovimiento.SALIDA, "1"))).isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> inventario.mover(icaId, usuarioParcona,
                movimiento(melaminaIcaId, TipoMovimiento.SALIDA, "1"))).isInstanceOf(ReglaNegocioException.class);
        StockMinimoForm minimo = new StockMinimoForm();
        minimo.setStockMinimo(new BigDecimal("99"));
        assertThatThrownBy(() -> inventario.cambiarMinimo(parconaId, melaminaIcaId, minimo))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(stock(melaminaIcaId)).isEqualByComparingTo("8");
        assertThat(movimientos.count()).isEqualTo(4);
    }

    @Test
    void stockMinimoPuedeSuperarExistenciasYActivaAlertaLocal() {
        StockMinimoForm form = new StockMinimoForm();
        form.setStockMinimo(new BigDecimal("25"));
        inventario.cambiarMinimo(parconaId, melaminaParconaId, form);
        assertThat(inventario.resumen(parconaId).stockBajo()).isEqualTo(3);
        assertThat(inventario.resumen(icaId).stockBajo()).isZero();
        form.setStockMinimo(new BigDecimal("-1"));
        assertThatThrownBy(() -> inventario.cambiarMinimo(parconaId, melaminaParconaId, form))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void productoInactivoNoAdmiteMovimientosNiNuevosRegistros() {
        productoService.cambiarEstado(melaminaId);
        assertThatThrownBy(() -> inventario.mover(parconaId, adminId,
                movimiento(melaminaParconaId, TipoMovimiento.ENTRADA, "1"))).isInstanceOf(ReglaNegocioException.class);
        RegistroInventarioForm form = new RegistroInventarioForm();
        form.setProductoId(melaminaId);
        assertThatThrownBy(() -> inventario.registrar(icaId, form)).isInstanceOf(ReglaNegocioException.class);
        assertThat(stock(melaminaParconaId)).isEqualByComparingTo("20");
    }

    @Test
    void dashboardLimitaDiezMovimientosPeroLaPantallaMuestraHistorialCompletoDeLaSede() throws Exception {
        for (int i = 0; i < 12; i++) {
            inventario.mover(parconaId, adminId, movimiento(melaminaParconaId, TipoMovimiento.ENTRADA, "1"));
        }
        assertThat(inventario.recientes(parconaId)).hasSize(10)
                .allMatch(m -> m.usuario().equals("Administrador general"));
        assertThat(inventario.recientes(icaId)).hasSize(2)
                .allMatch(m -> m.usuario().equals("Administrador Ica"));
        assertThat(inventario.historial(parconaId)).hasSizeGreaterThan(10);
        assertThat(inventario.historial(icaId)).hasSize(2);
        MockHttpSession sesion = login("admin");
        mvc.perform(get("/inventario").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Historial de movimientos"))));
        mvc.perform(get("/movimientos").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Historial de movimientos")))
                .andExpect(model().attributeExists("movimientos"));
    }

    @Test
    void peticionesNoPuedenCambiarSedeNiStockDirectamente() throws Exception {
        MockHttpSession sesion = login("admin");
        mvc.perform(post("/movimientos").session(sesion).with(csrf())
                .param("inventarioId", melaminaParconaId.toString()).param("sedeId", icaId.toString())
                .param("tipo", "ENTRADA").param("cantidad", "1").param("motivo", "Prueba web"))
                .andExpect(status().isForbidden());
        assertThat(stock(melaminaParconaId)).isEqualByComparingTo("20");
        assertThat(stock(melaminaIcaId)).isEqualByComparingTo("8");
        mvc.perform(post("/movimientos").session(sesion).with(csrf())
                .param("inventarioId", melaminaIcaId.toString()).param("tipo", "SALIDA")
                .param("cantidad", "1").param("motivo", "Otra sede")).andExpect(status().isForbidden());
        mvc.perform(post("/inventario/" + melaminaIcaId + "/minimo").session(sesion).with(csrf())
                .param("stockMinimo", "999")).andExpect(status().isForbidden());
        mvc.perform(get("/inventario").session(sesion)).andExpect(status().isOk())
                .andExpect(model().attributeExists("existencias"))
                .andExpect(content().string(not(containsString("Registrar movimiento"))));
        mvc.perform(get("/movimientos").session(sesion)).andExpect(status().isOk())
                .andExpect(model().attributeExists("movimientos"))
                .andExpect(content().string(not(containsString("action=\"/movimientos\""))));
        mvc.perform(post("/productos/guardar").session(sesion).with(csrf())
                .param("id", melaminaId.toString()).param("codigo", "FORZADO").param("nombre", "Melamina")
                .param("categoriaId", planchasId.toString()).param("unidadMedida", "UND")
                .param("precioCompra", "120").param("precioVenta", "150").param("estado", "true")
                .param("stock", "9999").param("sedeId", icaId.toString())).andExpect(redirectedUrl("/productos"));
        assertThat(stock(melaminaParconaId)).isEqualByComparingTo("20");
        assertThat(productoService.buscar(melaminaId).getCodigo()).startsWith("PRO-");
    }

    @Test
    void transferenciaWebActualizaAmbasSedesYVinculaLosMovimientos() throws Exception {
        MockHttpSession sesion = login("admin");
        mvc.perform(get("/transferencias").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Transferir desde")))
                .andExpect(content().string(containsString("Sede Parcona")))
                .andExpect(content().string(containsString("Sede Ica")));
        mvc.perform(post("/transferencias").session(sesion).with(csrf())
                .param("inventarioOrigenId", melaminaParconaId.toString())
                .param("sedeDestinoId", icaId.toString()).param("cantidad", "5")
                .param("motivo", "Reposición para Ica"))
                .andExpect(redirectedUrl("/transferencias"));
        assertThat(stock(melaminaParconaId)).isEqualByComparingTo("15");
        assertThat(stock(melaminaIcaId)).isEqualByComparingTo("13");
        assertThat(movimientos.count()).isEqualTo(6);
        Transferencia registro = transferencias
                .findBySalidaInventarioSedeIdOrEntradaInventarioSedeIdOrderByIdDesc(parconaId, parconaId)
                .getFirst();
        assertThat(registro.getSalida().getTipo()).isEqualTo(TipoMovimiento.SALIDA);
        assertThat(registro.getEntrada().getTipo()).isEqualTo(TipoMovimiento.ENTRADA);
        assertThat(registro.getSalida().getCantidad()).isEqualByComparingTo("-5");
        assertThat(registro.getEntrada().getCantidad()).isEqualByComparingTo("5");
        mvc.perform(get("/movimientos").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Transferencia: Reposición para Ica")))
                .andExpect(content().string(not(containsString("action=\"/movimientos\""))));
        mvc.perform(post("/sede-trabajo").session(sesion).with(csrf())
                .param("sedeId", icaId.toString())).andExpect(redirectedUrl("/dashboard"));
        mvc.perform(get("/transferencias").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Reposición para Ica")));
        mvc.perform(get("/movimientos").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Transferencia: Reposición para Ica")));
    }

    @Test
    void transferenciaCreaInventarioDestinoSinDuplicarCatalogo() {
        Long triplayId = productos.findAll().stream().filter(p -> p.getNombre().contains("triplay"))
                .findFirst().orElseThrow().getId();
        Long origenId = item(parconaId, triplayId);
        inventario.mover(parconaId, adminId, movimiento(origenId, TipoMovimiento.ENTRADA, "4"));
        long productosAntes = productos.count();
        transferenciaService.transferir(parconaId, adminId, transferencia(origenId, icaId, "3"));
        Inventario destino = inventarios.findByProductoIdAndSedeId(triplayId, icaId).orElseThrow();
        assertThat(stock(origenId)).isEqualByComparingTo("1");
        assertThat(destino.getStock()).isEqualByComparingTo("3");
        assertThat(destino.getStockMinimo()).isEqualByComparingTo("0");
        assertThat(productos.count()).isEqualTo(productosAntes);
        assertThat(transferenciaService.historial(parconaId)).hasSize(1);
        assertThat(transferenciaService.historial(icaId)).hasSize(1);
    }

    @Test
    void transferenciaSinStockNoCreaInventarioEnDestino() {
        Long triplayId = productos.findAll().stream().filter(p -> p.getNombre().contains("triplay"))
                .findFirst().orElseThrow().getId();
        Long origenId = item(parconaId, triplayId);
        long movimientosAntes = movimientos.count();
        assertThatThrownBy(() -> transferenciaService.transferir(parconaId, adminId,
                transferencia(origenId, icaId, "1")))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("stock disponible");
        assertThat(inventarios.findByProductoIdAndSedeId(triplayId, icaId)).isEmpty();
        assertThat(movimientos.count()).isEqualTo(movimientosAntes);
        assertThat(transferencias.count()).isZero();
    }

    @Test
    void transferenciaInvalidaNoModificaNingunaSede() {
        long movimientosAntes = movimientos.count();
        for (TransferenciaForm form : new TransferenciaForm[]{
                transferencia(melaminaParconaId, icaId, "21"),
                transferencia(melaminaParconaId, icaId, "0.5"),
                transferencia(melaminaIcaId, icaId, "1"),
                transferencia(melaminaParconaId, parconaId, "1")}) {
            assertThatThrownBy(() -> transferenciaService.transferir(parconaId, adminId, form))
                    .isInstanceOf(ReglaNegocioException.class);
        }
        Sede sedeIca = sedes.findById(icaId).orElseThrow();
        sedeIca.setEstado(false);
        sedes.save(sedeIca);
        assertThatThrownBy(() -> transferenciaService.transferir(parconaId, adminId,
                transferencia(melaminaParconaId, icaId, "1"))).isInstanceOf(ReglaNegocioException.class);
        assertThat(stock(melaminaParconaId)).isEqualByComparingTo("20");
        assertThat(stock(melaminaIcaId)).isEqualByComparingTo("8");
        assertThat(movimientos.count()).isEqualTo(movimientosAntes);
        assertThat(transferencias.count()).isZero();
    }

    @Test
    void transferenciaRevierteSalidaSiElDestinoSuperaLimite() {
        inventario.mover(icaId, adminId,
                movimiento(melaminaIcaId, TipoMovimiento.AJUSTE, "99999999"));
        long movimientosAntes = movimientos.count();
        assertThatThrownBy(() -> transferenciaService.transferir(parconaId, adminId,
                transferencia(melaminaParconaId, icaId, "1")))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("límite");
        assertThat(stock(melaminaParconaId)).isEqualByComparingTo("20");
        assertThat(stock(melaminaIcaId)).isEqualByComparingTo("99999999");
        assertThat(movimientos.count()).isEqualTo(movimientosAntes);
        assertThat(transferencias.count()).isZero();
    }

    @Test
    void administradorDeSedeNoPuedeTransferirAunqueLlameAlServicio() {
        Long adminIcaId = usuarios.findByUsuario("admin.ica").orElseThrow().getId();
        assertThatThrownBy(() -> transferenciaService.transferir(icaId, adminIcaId,
                transferencia(melaminaIcaId, parconaId, "1")))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("administrador general");
        assertThat(transferencias.count()).isZero();
    }

    @Test
    void salidaConcurrenteNoPermiteVenderDosVecesElMismoStock() throws Exception {
        var inicio = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tarea = (java.util.concurrent.Callable<Boolean>) () -> {
                inicio.await(5, TimeUnit.SECONDS);
                try {
                    inventario.mover(parconaId, adminId, movimiento(melaminaParconaId, TipoMovimiento.SALIDA, "12"));
                    return true;
                } catch (ReglaNegocioException e) {
                    return false;
                }
            };
            var primera = pool.submit(tarea);
            var segunda = pool.submit(tarea);
            inicio.countDown();
            boolean resultadoA = primera.get(10, TimeUnit.SECONDS);
            boolean resultadoB = segunda.get(10, TimeUnit.SECONDS);
            assertThat(resultadoA ^ resultadoB).isTrue();
            assertThat(stock(melaminaParconaId)).isEqualByComparingTo("8");
            assertThat(movimientos.count()).isEqualTo(5);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void stockEnMinimo() throws Exception {
        var minimo = new StockMinimoForm();
        minimo.setStockMinimo(new BigDecimal("20"));
        inventario.cambiarMinimo(parconaId, melaminaParconaId, minimo);

        var producto = inventario.listar(parconaId).stream()
                .filter(i -> i.id().equals(melaminaParconaId)).findFirst().orElseThrow();
        assertThat(producto.getSituacion()).isEqualTo("Stock bajo");
        assertThat(producto.getTextoBusqueda()).contains("Stock bajo");

        var sesion = login("admin");
        String html = mvc.perform(get("/inventario").session(sesion)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int inicio = html.indexOf(producto.nombre());
        assertThat(inicio).isGreaterThanOrEqualTo(0);
        assertThat(html.substring(inicio, html.indexOf("</tr>", inicio)))
                .contains("Stock bajo", "warning").doesNotContain("Disponible");

        var pagina = mvc.perform(get("/dashboard").session(sesion)).andExpect(status().isOk()).andReturn();
        var indicadores = (DashboardService.Informe) pagina.getModelAndView().getModel().get("indicadores");
        assertThat(indicadores.stockBajo()).isEqualTo(2);
        assertThat(indicadores.alertas()).anySatisfy(alerta -> {
            assertThat(alerta.nombre()).isEqualTo(producto.nombre());
            assertThat(alerta.situacion()).isEqualTo("Stock bajo");
        });
    }
}
