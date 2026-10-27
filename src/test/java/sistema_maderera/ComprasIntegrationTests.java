package sistema_maderera;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import sistema_maderera.dto.*;
import sistema_maderera.entity.*;
import sistema_maderera.exception.*;
import sistema_maderera.repository.*;
import sistema_maderera.service.*;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:compras_test;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ComprasIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired CompraService compras;
    @Autowired CompraRepository compraRepository;
    @Autowired ProveedorService proveedores;
    @Autowired ProveedorRepository proveedorRepository;
    @Autowired EmpresaRepository empresas;
    @Autowired SedeRepository sedes;
    @Autowired UsuarioRepository usuarios;
    @Autowired InventarioRepository inventarios;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired MovimientoInventarioRepository movimientos;
    @Autowired DemoDataService demo;
    @Autowired JdbcTemplate jdbc;
    private Long empresaId;
    private Long parconaId;
    private Long icaId;
    private Long adminId;
    private Long proveedorId;

    @BeforeEach
    void preparar() {
        compraRepository.deleteAll();
        proveedorRepository.deleteAll();
        movimientos.deleteAll();
        inventarios.deleteAll();
        productos.deleteAll();
        categorias.deleteAll();
        usuarios.deleteAll();
        sedes.deleteAll();
        empresas.deleteAll();
        demo.inicializar("ClaveDePrueba1");
        var admin = usuarios.findByUsuario("admin").orElseThrow();
        adminId = admin.getId();
        empresaId = admin.getEmpresa().getId();
        parconaId = usuarios.findByUsuario("consulta").orElseThrow().getSede().getId();
        icaId = usuarios.findByUsuario("admin.ica").orElseThrow().getSede().getId();
        proveedorId = proveedores.guardar(empresaId, proveedor("Maderas del Sur", "20123456789")).getId();
    }

    private ProveedorForm proveedor(String nombre, String ruc) {
        var form = new ProveedorForm();
        form.setNombre(nombre);
        form.setTipoIdentificacion(TipoIdentificacion.RUC);
        form.setNumeroDocumento(ruc);
        form.setTelefono("999123456");
        form.setDireccion("Av. Los Álamos 123");
        return form;
    }

    private Producto madera() { return productos.findAll().stream().filter(p -> "P²".equals(p.getUnidadMedida())).findFirst().orElseThrow(); }
    private BigDecimal stock(Long sedeId) { return inventarios.findByProductoIdAndSedeId(madera().getId(), sedeId).orElseThrow().getStock(); }
    private DetalleCompraForm linea(Long producto, String cantidad, String precio) {
        var linea = new DetalleCompraForm();
        linea.setProductoId(producto);
        linea.setCantidad(new BigDecimal(cantidad));
        linea.setPrecio(new BigDecimal(precio));
        return linea;
    }
    private CompraForm compra(String referencia) {
        var form = new CompraForm();
        form.setProveedorId(proveedorId);
        form.setFecha(compras.hoy());
        form.setReferencia(referencia);
        form.setDetalles("10 listones\nMedidas libres <prueba>");
        form.getLineas().add(linea(madera().getId(), "16.6666", "9"));
        return form;
    }
    private MockHttpSession login(String nombre) throws Exception {
        var sesion = (MockHttpSession) mvc.perform(post("/login").with(csrf()).param("usuario", nombre).param("password", "ClaveDePrueba1"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        if (nombre.equals("admin")) mvc.perform(post("/sede-trabajo").session(sesion).with(csrf()).param("sedeId", parconaId.toString())).andExpect(redirectedUrl("/dashboard"));
        return sesion;
    }

    @Test
    void pendienteNoMueveStockYConfirmacionSumaDecimalesSoloEnSuSede() {
        BigDecimal antes = stock(parconaId), otra = stock(icaId);
        long movs = movimientos.count();
        var precioVenta = madera().getPrecioVenta();
        var doc = compras.guardar(parconaId, adminId, compra(" f001-0001 "));
        assertThat(doc.getReferencia()).isEqualTo("F001-0001");
        assertThat(doc.getTotal()).isEqualByComparingTo("150.00");
        assertThat(stock(parconaId)).isEqualByComparingTo(antes);
        assertThat(movimientos.count()).isEqualTo(movs);
        var confirmado = compras.confirmar(parconaId, adminId, doc.getId(), doc.getVersion());
        assertThat(confirmado.getEstado()).isEqualTo(EstadoCompra.CONFIRMADA);
        assertThat(stock(parconaId)).isEqualByComparingTo(antes.add(new BigDecimal("16.6666")));
        assertThat(stock(icaId)).isEqualByComparingTo(otra);
        assertThat(movimientos.count()).isEqualTo(movs + 1);
        assertThat(madera().getPrecioVenta()).isEqualByComparingTo(precioVenta);
        var movimiento = movimientos.findAll().stream().filter(m -> m.getMotivo().contains(doc.getCodigo())).findFirst().orElseThrow();
        assertThat(movimiento.getTipo()).isEqualTo(TipoMovimiento.ENTRADA);
        assertThat(movimiento.getStockAnterior()).isEqualByComparingTo(antes);
        assertThat(movimiento.getStockPosterior()).isEqualByComparingTo(stock(parconaId));
        assertThatThrownBy(() -> compras.confirmar(parconaId, adminId, confirmado.getId(), confirmado.getVersion())).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> compras.formulario(parconaId, confirmado.getId())).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> compras.descartar(parconaId, adminId, confirmado.getId(), confirmado.getVersion())).isInstanceOf(ReglaNegocioException.class);
        var proveedor = proveedores.formulario(empresaId, proveedorId);
        proveedor.setNombre("Nombre nuevo");
        proveedores.guardar(empresaId, proveedor);
        assertThat(compras.ver(parconaId, doc.getId()).getProveedorNombre()).isEqualTo("Maderas del Sur");
    }

    @Test
    void editarReemplazaLineasSinDuplicarlasYDescartarLimpiaDetalles() {
        BigDecimal antes = stock(parconaId);
        var doc = compras.guardar(parconaId, adminId, compra("F001-2"));
        var form = compras.formulario(parconaId, doc.getId());
        form.getLineas().getFirst().setCantidad(new BigDecimal("0.2500"));
        form.getLineas().getFirst().setPrecio(new BigDecimal("13"));
        var cambiado = compras.guardar(parconaId, adminId, form);
        assertThat(compras.ver(parconaId, doc.getId()).getLineas()).hasSize(1);
        assertThat(cambiado.getTotal()).isEqualByComparingTo("3.25");
        assertThatThrownBy(() -> compras.guardar(parconaId, adminId, form)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cambió");
        compras.descartar(parconaId, adminId, cambiado.getId(), cambiado.getVersion());
        assertThat(compraRepository.count()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from detalles_compra", Long.class)).isZero();
        assertThat(stock(parconaId)).isEqualByComparingTo(antes);
    }

    @Test
    void agregaProductoDelCatalogoASedeNuevaSoloAlConfirmar() {
        Producto nuevo = new Producto();
        nuevo.setCodigo("PRO-COMPRA"); nuevo.setNombre("Clavos de prueba"); nuevo.setUnidadMedida("KG");
        nuevo.setCategoria(categorias.findAll().getFirst());
        nuevo = productos.save(nuevo);
        var form = compra("F001-3");
        form.getLineas().clear();
        form.getLineas().add(linea(nuevo.getId(), "0.1538", "13"));
        var doc = compras.guardar(parconaId, adminId, form);
        assertThat(inventarios.existsByProductoIdAndSedeId(nuevo.getId(), parconaId)).isFalse();
        compras.confirmar(parconaId, adminId, doc.getId(), doc.getVersion());
        var registro = inventarios.findByProductoIdAndSedeId(nuevo.getId(), parconaId).orElseThrow();
        assertThat(registro.getStock()).isEqualByComparingTo("0.1538");
        assertThat(registro.getStockMinimo()).isEqualByComparingTo("0");
        assertThat(inventarios.existsByProductoIdAndSedeId(nuevo.getId(), icaId)).isFalse();
    }

    @Test
    void validaComprobantesCantidadesUnidadesFechasYProveedor() {
        compras.guardar(parconaId, adminId, compra("F001-4"));
        assertThatThrownBy(() -> compras.guardar(icaId, adminId, compra(" f001-4 "))).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya está registrado");
        var form = compra("F001-5");
        form.setFecha(compras.hoy().plusDays(1));
        assertThatThrownBy(() -> compras.guardar(parconaId, adminId, form)).isInstanceOf(ReglaNegocioException.class);
        form.setFecha(compras.hoy());
        form.getLineas().getFirst().setCantidad(new BigDecimal("1.12345"));
        assertThatThrownBy(() -> compras.guardar(parconaId, adminId, form)).isInstanceOf(ReglaNegocioException.class);
        form.getLineas().getFirst().setCantidad(BigDecimal.ZERO);
        assertThatThrownBy(() -> compras.guardar(parconaId, adminId, form)).isInstanceOf(ReglaNegocioException.class);
        form.getLineas().getFirst().setCantidad(BigDecimal.ONE);
        form.getLineas().add(linea(madera().getId(), "1", "2"));
        assertThatThrownBy(() -> compras.guardar(parconaId, adminId, form)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("repetido");
        form.getLineas().clear();
        var unidad = productos.findAll().stream().filter(p -> "UND".equals(p.getUnidadMedida())).findFirst().orElseThrow();
        form.getLineas().add(linea(unidad.getId(), "0.5", "2"));
        assertThatThrownBy(() -> compras.guardar(parconaId, adminId, form)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("enteras");
        form.getLineas().getFirst().setCantidad(BigDecimal.ONE);
        proveedores.cambiarEstado(empresaId, proveedorId);
        assertThatThrownBy(() -> compras.guardar(parconaId, adminId, form)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("activo");
    }

    @Test
    void confirmacionRevalidaProveedorProductoYRevierteTodoSiDesbordaStock() {
        var form = compra("F001-6");
        var unidad = productos.findAll().stream().filter(p -> "UND".equals(p.getUnidadMedida())).findFirst().orElseThrow();
        form.getLineas().add(linea(unidad.getId(), "1", "10"));
        var doc = compras.guardar(parconaId, adminId, form);
        proveedores.cambiarEstado(empresaId, proveedorId);
        assertThatThrownBy(() -> compras.confirmar(parconaId, adminId, doc.getId(), doc.getVersion())).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("inactivo");
        proveedores.cambiarEstado(empresaId, proveedorId);
        unidad.setEstado(false); productos.save(unidad);
        assertThatThrownBy(() -> compras.confirmar(parconaId, adminId, doc.getId(), doc.getVersion())).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("inactivos");
        unidad.setEstado(true); productos.save(unidad);
        var inventario = inventarios.findByProductoIdAndSedeId(madera().getId(), parconaId).orElseThrow();
        inventario.setStock(new BigDecimal("99999999.9999")); inventarios.save(inventario);
        var stockUnidad = inventarios.findByProductoIdAndSedeId(unidad.getId(), parconaId).orElseThrow().getStock();
        long antes = movimientos.count();
        assertThatThrownBy(() -> compras.confirmar(parconaId, adminId, doc.getId(), doc.getVersion())).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("máximo");
        assertThat(inventarios.findByProductoIdAndSedeId(unidad.getId(), parconaId).orElseThrow().getStock()).isEqualByComparingTo(stockUnidad);
        assertThat(movimientos.count()).isEqualTo(antes);
        assertThat(compras.ver(parconaId, doc.getId()).getEstado()).isEqualTo(EstadoCompra.PENDIENTE);
    }

    @Test
    void proveedorValidaDocumentoDuplicadosEmpresaYVersion() {
        assertThatThrownBy(() -> proveedores.guardar(empresaId, proveedor("Otro", "20123456789"))).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> proveedores.guardar(empresaId, proveedor("Otro", "123"))).isInstanceOf(ReglaNegocioException.class);
        var form = proveedores.formulario(empresaId, proveedorId);
        form.setNombre("Modificado");
        proveedores.guardar(empresaId, form);
        assertThatThrownBy(() -> proveedores.guardar(empresaId, form)).isInstanceOf(ReglaNegocioException.class);
        Empresa otra = new Empresa(); otra.setNombreComercial("Otra empresa"); otra = empresas.save(otra);
        var ajeno = proveedores.guardar(otra.getId(), proveedor("Ajeno", "20999999999"));
        assertThatThrownBy(() -> proveedores.buscar(empresaId, ajeno.getId())).isInstanceOf(RecursoNoEncontradoException.class);
        var compra = compra("OTRO-1"); compra.setProveedorId(ajeno.getId());
        assertThatThrownBy(() -> compras.guardar(parconaId, adminId, compra)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void pantallasFlujoPostYPermisosPorRolYSede() throws Exception {
        var sesion = login("admin");
        mvc.perform(get("/proveedores").session(sesion)).andExpect(status().isOk()).andExpect(content().string(containsString("Maderas del Sur")));
        mvc.perform(get("/proveedores/nuevo").session(sesion)).andExpect(status().isOk());
        mvc.perform(get("/proveedores/" + proveedorId + "/editar").session(sesion)).andExpect(status().isOk());
        mvc.perform(get("/compras/nueva").session(sesion)).andExpect(status().isOk()).andExpect(content().string(containsString("Sede receptora")));
        mvc.perform(post("/compras/guardar").session(sesion).with(csrf())
                .param("proveedorId", proveedorId.toString()).param("fecha", compras.hoy().toString()).param("tipo", "FACTURA")
                .param("referencia", "F001-7").param("lineas[0].productoId", madera().getId().toString())
                .param("lineas[0].cantidad", "0.2500").param("lineas[0].precio", "13").param("detalles", "<script>alert(1)</script>"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("mensaje"));
        var doc = compraRepository.findAll().getFirst(); String ruta = "/compras/" + doc.getId();
        mvc.perform(get(ruta).session(sesion)).andExpect(status().isOk()).andExpect(content().string(containsString("&lt;script&gt;")));
        mvc.perform(get(ruta + "/editar").session(sesion)).andExpect(status().isOk()).andExpect(content().string(containsString("Descartar compra pendiente")));
        mvc.perform(get("/compras").session(sesion)).andExpect(status().isOk()).andExpect(content().string(containsString("F001-7")));
        var local = login("admin.ica");
        mvc.perform(get(ruta).session(local)).andExpect(status().isNotFound());
        mvc.perform(get("/compras").session(local)).andExpect(content().string(not(containsString("F001-7"))));
        for (String accion : List.of("confirmar", "descartar")) {
            mvc.perform(post(ruta + "/" + accion).session(sesion).param("version", doc.getVersion().toString())).andExpect(status().isForbidden());
            mvc.perform(post(ruta + "/" + accion).session(local).with(csrf()).param("version", doc.getVersion().toString())).andExpect(status().isNotFound());
        }
        var consulta = login("consulta");
        mvc.perform(get("/compras").session(consulta)).andExpect(status().isForbidden());
        mvc.perform(get("/proveedores").session(consulta)).andExpect(status().isForbidden());
        mvc.perform(post("/proveedores/" + proveedorId + "/estado").session(local).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(ruta + "/confirmar").session(sesion).with(csrf()).param("version", doc.getVersion().toString()))
                .andExpect(redirectedUrl(ruta)).andExpect(flash().attributeExists("mensaje"));
        mvc.perform(get(ruta).session(sesion)).andExpect(status().isOk()).andExpect(content().string(containsString("Ingreso confirmado")))
                .andExpect(content().string(not(containsString("Descartar compra pendiente"))));
    }

    @Test
    void descartarDesdeWebYErroresDeFormularioConservanDatos() throws Exception {
        var sesion = login("admin");
        mvc.perform(post("/compras/guardar").session(sesion).with(csrf()).param("referencia", "Conservar"))
                .andExpect(status().isOk()).andExpect(view().name("compra-form"))
                .andExpect(content().string(containsString("Conservar")));
        var doc = compras.guardar(parconaId, adminId, compra("F001-8"));
        mvc.perform(post("/compras/" + doc.getId() + "/descartar").session(sesion).with(csrf()).param("version", doc.getVersion().toString()))
                .andExpect(redirectedUrl("/compras")).andExpect(flash().attributeExists("mensaje"));
        assertThat(compraRepository.existsById(doc.getId())).isFalse();
    }

    @Test
    void dosConfirmacionesSimultaneasIngresanStockUnaSolaVez() throws Exception {
        var doc = compras.guardar(parconaId, adminId, compra("F001-9"));
        var stock = stock(parconaId); long antes = movimientos.count();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var inicio = new CountDownLatch(1);
            Callable<Boolean> confirmar = () -> {
                inicio.await(5, TimeUnit.SECONDS);
                try { compras.confirmar(parconaId, adminId, doc.getId(), doc.getVersion()); return true; }
                catch (ReglaNegocioException e) { return false; }
            };
            var a = pool.submit(confirmar); var b = pool.submit(confirmar); inicio.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            assertThat(stock(parconaId)).isEqualByComparingTo(stock.add(new BigDecimal("16.6666")));
            assertThat(movimientos.count()).isEqualTo(antes + 1);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void servicioRechazaSedeInactivaUsuarioAjenoYUnidadModificada() {
        var local = usuarios.findByUsuario("admin.ica").orElseThrow();
        assertThatThrownBy(() -> compras.guardar(parconaId, local.getId(), compra("F001-10"))).isInstanceOf(ReglaNegocioException.class);
        var doc = compras.guardar(parconaId, adminId, compra("F001-10"));
        var sede = sedes.findById(parconaId).orElseThrow(); sede.setEstado(false); sedes.save(sede);
        assertThatThrownBy(() -> compras.confirmar(parconaId, adminId, doc.getId(), doc.getVersion())).isInstanceOf(ReglaNegocioException.class);
        sede.setEstado(true); sedes.save(sede);
        var producto = madera(); producto.setUnidadMedida("KG"); productos.save(producto);
        assertThatThrownBy(() -> compras.confirmar(parconaId, adminId, doc.getId(), doc.getVersion())).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("unidad");
    }
}
