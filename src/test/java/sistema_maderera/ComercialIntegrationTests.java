package sistema_maderera;

import java.util.List;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ComercialIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ClienteService clientes;
    @Autowired ClienteRepository clienteRepository;
    @Autowired DocumentoVentaService documentos;
    @Autowired DocumentoVentaRepository documentoRepository;
    @Autowired CorrelativoDocumentoRepository correlativos;
    @Autowired NumeracionDocumentoService numeracion;
    @Autowired SedeService sedeService;
    @Autowired EmpresaRepository empresas;
    @Autowired SedeRepository sedes;
    @Autowired UsuarioRepository usuarios;
    @Autowired TransferenciaRepository transferencias;
    @Autowired MovimientoInventarioRepository movimientos;
    @Autowired InventarioRepository inventarios;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    @Autowired DemoDataService demo;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired CuentaEmpresaService cuentas;
    @Autowired CuentaEmpresaRepository cuentaRepository;
    @Autowired DashboardService dashboard;
    private Long cuentaId;
    private Long empresaId;
    private Long parconaId;
    private Long icaId;
    private Long adminId;

    @BeforeEach
    void preparar() {
        limpiarComercial();
        transferencias.deleteAll();
        movimientos.deleteAll();
        inventarios.deleteAll();
        productos.deleteAll();
        categorias.deleteAll();
        usuarios.deleteAll();
        sedes.deleteAll();
        empresas.deleteAll();
        demo.inicializar("ClaveDePrueba1");
        Usuario admin = usuarios.findByUsuario("admin").orElseThrow();
        adminId = admin.getId();
        empresaId = admin.getEmpresa().getId();
        CuentaEmpresaForm cuenta = new CuentaEmpresaForm();
        cuenta.setBanco("BCP"); cuenta.setNumero("1911234567890"); cuenta.setCelularYape("987654321");
        cuentas.agregar(empresaId, cuenta);
        cuentaId = cuentas.listar(empresaId).getFirst().getId();
        parconaId = usuarios.findByUsuario("consulta").orElseThrow().getSede().getId();
        icaId = usuarios.findByUsuario("admin.ica").orElseThrow().getSede().getId();
    }

    @AfterEach
    void limpiarComercial() {
        documentoRepository.deleteAll();
        correlativos.deleteAll();
        clienteRepository.deleteAll();
        cuentaRepository.deleteAll();
    }

    private ClienteForm cliente(String nombre, TipoIdentificacion tipo, String numero) {
        ClienteForm form = new ClienteForm();
        form.setNombre(nombre);
        form.setTipoIdentificacion(tipo);
        form.setNumeroDocumento(numero);
        return form;
    }

    private DocumentoVentaForm borrador(TipoDocumentoVenta tipo, Long clienteId) {
        DocumentoVentaForm form = new DocumentoVentaForm();
        form.setTipo(tipo);
        form.setClienteId(clienteId);
        form.setDetalles("10 listones de 1 x 2 x 10");
        return form;
    }

    private MockHttpSession login(String nombre) throws Exception {
        MockHttpSession sesion = (MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("usuario", nombre).param("password", "ClaveDePrueba1"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        if ("admin".equals(nombre)) mvc.perform(post("/sede-trabajo").session(sesion).with(csrf())
                .param("sedeId", parconaId.toString())).andExpect(redirectedUrl("/dashboard"));
        return sesion;
    }

    private DetalleVentaForm linea(Long producto, String cantidad, String precio, String cobrar) {
        DetalleVentaForm item = new DetalleVentaForm();
        item.setProductoId(producto);
        item.setCantidad(new BigDecimal(cantidad));
        item.setPrecio(new BigDecimal(precio));
        item.setImporteCobrar(cobrar == null ? null : new BigDecimal(cobrar));
        return item;
    }

    private Producto madera() {
        return productos.findAll().stream().filter(p -> "P²".equals(p.getUnidadMedida())).findFirst().orElseThrow();
    }

    private PagoVentaForm pago(MedioPago medio, String monto, String codigo, String banco) {
        PagoVentaForm form = new PagoVentaForm();
        form.setMedio(medio);
        form.setRecibido(new BigDecimal(monto));
        form.setReferencia(codigo);
        form.setBanco(banco);
        if (medio == MedioPago.YAPE || medio == MedioPago.TRANSFERENCIA && banco != null && !banco.isBlank()) form.setCuentaDestinoId(cuentaId);
        return form;
    }

    private DocumentoVentaForm ventaPagada(String cantidad, String precio, String recibido) {
        DocumentoVentaForm form = borrador(TipoDocumentoVenta.BOLETA, null);
        form.getLineas().add(linea(madera().getId(), cantidad, precio, null));
        form.getPagos().add(pago(MedioPago.EFECTIVO, recibido, null, null));
        return form;
    }

    private DocumentoVenta ventaConfirmada() {
        DocumentoVenta doc = documentos.guardarBorrador(parconaId, adminId, ventaPagada("1", "10", "20"));
        return documentos.confirmar(parconaId, adminId, doc.getId(), doc.getVersion());
    }

    @Test
    void cotizacionEmitidaSeImprimeSinStockNiCobrosYSeConvierteUnaSolaVez() throws Exception {
        var form = borrador(TipoDocumentoVenta.COTIZACION, null);
        form.getLineas().add(linea(madera().getId(), "16.6666", "9", "150"));
        var pendiente = documentos.guardarBorrador(parconaId, adminId, form);
        var stock = inventarios.findByProductoIdAndSedeId(madera().getId(), parconaId).orElseThrow().getStock();
        long movimientosAntes = movimientos.count();
        var sesion = login("admin");
        String ruta = "/ventas/" + pendiente.getId();
        mvc.perform(get(ruta).session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Emitir cotización")));
        mvc.perform(post(ruta + "/emitir-cotizacion").session(sesion).with(csrf()).param("version", pendiente.getVersion().toString()))
                .andExpect(redirectedUrl(ruta)).andExpect(flash().attributeExists("mensaje"));
        var cotizacion = documentos.ver(parconaId, pendiente.getId());
        assertThat(cotizacion.getEstado()).isEqualTo(EstadoDocumentoVenta.EMITIDO);
        assertThat(cotizacion.getCodigoDocumento()).isEqualTo("C001-0001");
        assertThat(cotizacion.getPagos()).isEmpty();
        assertThat(cotizacion.getCobroVigente()).isEqualByComparingTo("0");
        assertThat(inventarios.findByProductoIdAndSedeId(madera().getId(), parconaId).orElseThrow().getStock()).isEqualByComparingTo(stock);
        assertThat(movimientos.count()).isEqualTo(movimientosAntes);
        mvc.perform(get(ruta + "/imprimir").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("C001-0001")))
                .andExpect(content().string(containsString("16.6666")))
                .andExpect(content().string(not(containsString("Al contado"))))
                .andExpect(content().string(not(containsString("class=\"pagos\""))));
        var venta = documentos.convertirCotizacion(parconaId, adminId, cotizacion.getId(), cotizacion.getVersion(), TipoDocumentoVenta.BOLETA);
        assertThat(venta.getCorrelativo()).isNull();
        assertThat(venta.getTotal()).isEqualByComparingTo("150");
        assertThat(venta.getPagos()).isEmpty();
        assertThat(venta.getDocumentoOrigenId()).isEqualTo(cotizacion.getId());
        var repetida = documentos.convertirCotizacion(parconaId, adminId, cotizacion.getId(), cotizacion.getVersion(), TipoDocumentoVenta.FACTURA);
        assertThat(repetida.getId()).isEqualTo(venta.getId());
        mvc.perform(get(ruta).session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Ya existe una venta vinculada")));
        assertThatThrownBy(() -> documentos.confirmar(parconaId, adminId, venta.getId(), venta.getVersion()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("pago");
        var editar = documentos.formulario(parconaId, venta.getId());
        editar.getLineas().getFirst().setCantidad(new BigDecimal("1"));
        editar.getPagos().add(pago(MedioPago.EFECTIVO, "150", null, null));
        var guardada = documentos.guardarBorrador(parconaId, adminId, editar);
        var confirmada = documentos.confirmar(parconaId, adminId, guardada.getId(), guardada.getVersion());
        assertThat(confirmada.getCodigoDocumento()).isEqualTo("B001-0001");
        assertThat(documentos.ver(parconaId, cotizacion.getId()).getLineas().getFirst().getCantidad()).isEqualByComparingTo("16.6666");
        assertThat(documentos.convertirCotizacion(parconaId, adminId, cotizacion.getId(), cotizacion.getVersion(), TipoDocumentoVenta.BOLETA).getId()).isEqualTo(confirmada.getId());
    }

    @Test
    void emisionYConversionConcurrentesNoDuplicanCotizacionNiVenta() throws Exception {
        var form = borrador(TipoDocumentoVenta.COTIZACION, null);
        form.getLineas().add(linea(madera().getId(), "1", "10", null));
        var doc = documentos.guardarBorrador(parconaId, adminId, form);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var inicio = new CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> emitir = () -> {
                inicio.await(5, TimeUnit.SECONDS);
                try { documentos.emitirCotizacion(parconaId, adminId, doc.getId(), doc.getVersion()); return true; }
                catch (ReglaNegocioException e) { return false; }
            };
            var a = pool.submit(emitir);
            var b = pool.submit(emitir);
            inicio.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            var emitida = documentos.ver(parconaId, doc.getId());
            var convertirInicio = new CountDownLatch(1);
            java.util.concurrent.Callable<Long> convertir = () -> {
                convertirInicio.await(5, TimeUnit.SECONDS);
                return documentos.convertirCotizacion(parconaId, adminId, doc.getId(), emitida.getVersion(), TipoDocumentoVenta.BOLETA).getId();
            };
            var c = pool.submit(convertir);
            var d = pool.submit(convertir);
            convertirInicio.countDown();
            assertThat(c.get(15, TimeUnit.SECONDS)).isEqualTo(d.get(15, TimeUnit.SECONDS));
            assertThat(documentoRepository.count()).isEqualTo(2);
            assertThat(numeracion.proximos(parconaId).get(TipoDocumentoVenta.COTIZACION).getCodigo()).isEqualTo("C001-0002");
            assertThat(numeracion.proximos(parconaId).get(TipoDocumentoVenta.BOLETA).getCodigo()).isEqualTo("B001-0001");
        } finally { pool.shutdownNow(); }
    }

    @Test
    void cotizacionValidaEstadoVersionPermisosYPermiteReconvertirTrasDescartar() throws Exception {
        var form = borrador(TipoDocumentoVenta.COTIZACION, null);
        var vacia = documentos.guardarBorrador(parconaId, adminId, form);
        assertThatThrownBy(() -> documentos.emitirCotizacion(parconaId, adminId, vacia.getId(), vacia.getVersion())).isInstanceOf(ReglaNegocioException.class);
        form.setId(vacia.getId());
        form.setVersion(vacia.getVersion());
        form.getLineas().add(linea(madera().getId(), "1", "10", null));
        var pendiente = documentos.guardarBorrador(parconaId, adminId, form);
        String ruta = "/ventas/" + pendiente.getId();
        mvc.perform(post(ruta + "/emitir-cotizacion").session(login("admin")).param("version", pendiente.getVersion().toString())).andExpect(status().isForbidden());
        mvc.perform(post(ruta + "/emitir-cotizacion").session(login("admin.ica")).with(csrf()).param("version", pendiente.getVersion().toString())).andExpect(status().isNotFound());
        mvc.perform(post(ruta + "/emitir-cotizacion").session(login("consulta")).with(csrf()).param("version", pendiente.getVersion().toString())).andExpect(status().isForbidden());
        assertThatThrownBy(() -> documentos.emitirCotizacion(parconaId, adminId, pendiente.getId(), -1L)).isInstanceOf(ReglaNegocioException.class);
        var emitida = documentos.emitirCotizacion(parconaId, adminId, pendiente.getId(), pendiente.getVersion());
        assertThatThrownBy(() -> documentos.emitirCotizacion(parconaId, adminId, emitida.getId(), emitida.getVersion())).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> documentos.descartarPendiente(parconaId, adminId, emitida.getId(), emitida.getVersion())).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> documentos.anular(parconaId, adminId, emitida.getId(), emitida.getVersion(), "Error")).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> documentos.convertirCotizacion(parconaId, adminId, emitida.getId(), emitida.getVersion(), TipoDocumentoVenta.COTIZACION)).isInstanceOf(ReglaNegocioException.class);
        for (String usuario : List.of("admin.ica", "consulta")) {
            mvc.perform(post(ruta + "/convertir").session(login(usuario)).with(csrf()).param("version", emitida.getVersion().toString()).param("tipo", "BOLETA"))
                    .andExpect(status().is(usuario.equals("consulta") ? 403 : 404));
        }
        mvc.perform(post(ruta + "/convertir").session(login("admin")).with(csrf()).param("version", emitida.getVersion().toString()).param("tipo", "NOTA_VENTA"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("mensaje"));
        var copia = documentos.ventaDeCotizacion(parconaId, emitida.getId());
        assertThat(copia.getTipo()).isEqualTo(TipoDocumentoVenta.NOTA_VENTA);
        var cambio = documentos.formulario(parconaId, copia.getId());
        cambio.setTipo(TipoDocumentoVenta.COTIZACION);
        assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId, cambio)).isInstanceOf(ReglaNegocioException.class);
        documentos.descartarPendiente(parconaId, adminId, copia.getId(), copia.getVersion());
        var otra = documentos.convertirCotizacion(parconaId, adminId, emitida.getId(), emitida.getVersion(), TipoDocumentoVenta.FACTURA);
        assertThat(otra.getId()).isNotEqualTo(copia.getId());
        var factura = documentos.formulario(parconaId, otra.getId());
        factura.getPagos().add(pago(MedioPago.EFECTIVO, "10", null, null));
        assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId, factura)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("RUC");
    }

    @Test
    void descartarCopiaEliminaSusDatosSinAfectarOriginalStockONumeracion() throws Exception {
        var original = ventaConfirmada();
        original = documentos.anular(parconaId, adminId, original.getId(), original.getVersion(), "Corregir");
        var copia = documentos.copiarAnulado(parconaId, adminId, original.getId(), original.getVersion());
        var stock = inventarios.findByProductoIdAndSedeId(madera().getId(), parconaId).orElseThrow().getStock();
        var numero = numeracion.proximos(parconaId).get(TipoDocumentoVenta.BOLETA).getCodigo();
        long movimientosAntes = movimientos.count();
        var sesion = login("admin");
        String ruta = "/ventas/" + copia.getId();
        mvc.perform(get(ruta + "/editar").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Descartar pendiente")))
                .andExpect(content().string(containsString("action=\"" + ruta + "/descartar\"")));
        mvc.perform(get(ruta).session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sí, eliminar pendiente")));
        mvc.perform(post(ruta + "/descartar").session(sesion).with(csrf()).param("version", copia.getVersion().toString()))
                .andExpect(redirectedUrl("/ventas")).andExpect(flash().attributeExists("mensaje"));
        assertThat(documentoRepository.existsById(copia.getId())).isFalse();
        assertThat(documentos.ver(parconaId, original.getId()).getEstado()).isEqualTo(EstadoDocumentoVenta.ANULADO);
        assertThat(inventarios.findByProductoIdAndSedeId(madera().getId(), parconaId).orElseThrow().getStock()).isEqualByComparingTo(stock);
        assertThat(movimientos.count()).isEqualTo(movimientosAntes);
        assertThat(numeracion.proximos(parconaId).get(TipoDocumentoVenta.BOLETA).getCodigo()).isEqualTo(numero);
        var nueva = documentos.copiarAnulado(parconaId, adminId, original.getId(), original.getVersion());
        assertThat(nueva.getId()).isNotEqualTo(copia.getId());
        assertThat(documentos.ver(parconaId, nueva.getId()).getLineas()).hasSize(1);
    }

    @Test
    void descartarRechazaCambiosConcurrentesDocumentosEmitidosYAccesoAjeno() throws Exception {
        var pendiente = documentos.guardarBorrador(parconaId, adminId, ventaPagada("1", "10", "20"));
        String ruta = "/ventas/" + pendiente.getId() + "/descartar";
        mvc.perform(post(ruta).session(login("admin")).param("version", pendiente.getVersion().toString())).andExpect(status().isForbidden());
        mvc.perform(post(ruta).session(login("consulta")).with(csrf()).param("version", pendiente.getVersion().toString())).andExpect(status().isForbidden());
        mvc.perform(post(ruta).session(login("admin.ica")).with(csrf()).param("version", pendiente.getVersion().toString())).andExpect(status().isNotFound());
        assertThatThrownBy(() -> documentos.descartarPendiente(parconaId, adminId, pendiente.getId(), -1L))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cambió");
        var confirmada = documentos.confirmar(parconaId, adminId, pendiente.getId(), pendiente.getVersion());
        assertThatThrownBy(() -> documentos.descartarPendiente(parconaId, adminId, confirmada.getId(), confirmada.getVersion()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("pendientes");
        var anulada = documentos.anular(parconaId, adminId, confirmada.getId(), confirmada.getVersion(), "Error");
        assertThatThrownBy(() -> documentos.descartarPendiente(parconaId, adminId, anulada.getId(), anulada.getVersion()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("pendientes");
        assertThat(documentoRepository.existsById(anulada.getId())).isTrue();
    }

    @Test
    void impresionIncluyeDatosGuardadosParaCadaTipoSinModificarVenta() throws Exception {
        var cliente = clientes.guardar(empresaId, cliente("Cliente impresión", TipoIdentificacion.RUC, "20123456789"));
        var sesion = login("admin");
        for (var tipo : List.of(TipoDocumentoVenta.BOLETA, TipoDocumentoVenta.FACTURA, TipoDocumentoVenta.NOTA_VENTA)) {
            var form = ventaPagada("0.2500", "13", "10");
            form.setTipo(tipo);
            form.setClienteId(cliente.getId());
            form.setDetalles("Medidas libres\n<script>alert(1)</script>");
            form.getLineas().getFirst().setImporteCobrar(new BigDecimal("3.50"));
            var pendiente = documentos.guardarBorrador(parconaId, adminId, form);
            mvc.perform(get("/ventas/" + pendiente.getId() + "/imprimir").session(sesion))
                    .andExpect(redirectedUrl("/ventas/" + pendiente.getId()));
            var venta = documentos.confirmar(parconaId, adminId, pendiente.getId(), pendiente.getVersion());
            long movimientosAntes = movimientos.count();
            mvc.perform(get("/ventas/" + venta.getId() + "/imprimir").session(sesion)).andExpect(status().isOk())
                    .andExpect(view().name("venta-impresion"))
                    .andExpect(content().string(containsString(venta.getCodigoDocumento())))
                    .andExpect(content().string(containsString("Cliente impresión")))
                    .andExpect(content().string(containsString(">0.25</td>")))
                    .andExpect(content().string(containsString("S/ 2.97")))
                    .andExpect(content().string(containsString("S/ 0.53")))
                    .andExpect(content().string(containsString("S/ 3.50")))
                    .andExpect(content().string(containsString("&lt;script&gt;")))
                    .andExpect(content().string(not(containsString("<script>alert"))));
            assertThat(movimientos.count()).isEqualTo(movimientosAntes);
            assertThat(documentos.ver(parconaId, venta.getId()).getVersion()).isEqualTo(venta.getVersion());
        }
    }

    @Test
    void impresionAnuladaEsHistoricaYRespetaSedeYRol() throws Exception {
        var venta = ventaConfirmada();
        documentos.anular(parconaId, adminId, venta.getId(), venta.getVersion(), "Corregir medidas");
        String ruta = "/ventas/" + venta.getId() + "/imprimir";
        mvc.perform(get(ruta).session(login("admin"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("DOCUMENTO ANULADO")))
                .andExpect(content().string(containsString("Corregir medidas")))
                .andExpect(content().string(containsString("Cobro vigente: S/ 0.00")))
                .andExpect(content().string(containsString("Pagos originales · Revertidos")));
        mvc.perform(get(ruta).session(login("admin.ica"))).andExpect(status().isNotFound());
        mvc.perform(get(ruta).session(login("consulta"))).andExpect(status().isForbidden());
    }

    @Test
    void anularReponeStockActualConProductoInactivoYRevierteCobrosSinBorrarHistoria() {
        DocumentoVenta doc = ventaConfirmada();
        long antes = movimientos.count();
        Producto producto = madera();
        producto.setEstado(false);
        productos.save(producto);
        Inventario inventario = inventarios.findByProductoIdAndSedeId(producto.getId(), parconaId).orElseThrow();
        inventario.setStock(new BigDecimal("7"));
        inventarios.save(inventario);
        DocumentoVenta anulada = documentos.anular(parconaId, adminId, doc.getId(), doc.getVersion(), " Error en cantidades ");
        assertThat(anulada.getEstado()).isEqualTo(EstadoDocumentoVenta.ANULADO);
        assertThat(anulada.getAnuladoEn()).isNotNull();
        assertThat(anulada.getAnuladoPor().getId()).isEqualTo(adminId);
        assertThat(anulada.getMotivoAnulacion()).isEqualTo("Error en cantidades");
        assertThat(anulada.getCodigoDocumento()).isEqualTo("B001-0001");
        assertThat(anulada.getTotal()).isEqualByComparingTo("10");
        assertThat(anulada.getVuelto()).isEqualByComparingTo("10");
        assertThat(anulada.getCobroVigente()).isZero();
        assertThat(anulada.getPagos().getFirst().getRecibido()).isEqualByComparingTo("20");
        assertThat(anulada.getPagos().getFirst().getRevertido()).isEqualByComparingTo("10");
        assertThat(anulada.getPagos().getFirst().getAplicadoNeto()).isZero();
        assertThat(inventarios.findByProductoIdAndSedeId(producto.getId(), parconaId).orElseThrow().getStock()).isEqualByComparingTo("8");
        assertThat(inventarios.findByProductoIdAndSedeId(producto.getId(), icaId).orElseThrow().getStock()).isEqualByComparingTo("35");
        assertThat(movimientos.count()).isEqualTo(antes + 1);
        assertThat(movimientos.findAll().stream().filter(m -> m.getMotivo().startsWith("Anulación")).findFirst().orElseThrow().getCantidad())
                .isEqualByComparingTo("1");
        assertThatThrownBy(() -> documentos.anular(parconaId, adminId, doc.getId(), anulada.getVersion(), "otra"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(movimientos.count()).isEqualTo(antes + 1);
        assertThat(numeracion.proximos(parconaId).get(TipoDocumentoVenta.BOLETA).getNumero()).isEqualTo("0002");
    }

    @Test
    void copiaConservaDatosAjustesYPagosYConfirmaConNuevoNumero() {
        Cliente cliente = clientes.guardar(empresaId, cliente("Empresa cliente", TipoIdentificacion.RUC, "20123456789"));
        DocumentoVentaForm form = ventaPagada("0.25", "13", "2");
        form.setTipo(TipoDocumentoVenta.FACTURA);
        form.setClienteId(cliente.getId());
        form.getLineas().getFirst().setImporteCobrar(new BigDecimal("3.50"));
        form.getPagos().add(pago(MedioPago.YAPE, "1", "YP-A", null));
        form.getPagos().add(pago(MedioPago.TRANSFERENCIA, "1", "TR-A", "BCP"));
        DocumentoVenta original = documentos.guardarBorrador(parconaId, adminId, form);
        original = documentos.confirmar(parconaId, adminId, original.getId(), original.getVersion());
        original = documentos.anular(parconaId, adminId, original.getId(), original.getVersion(), "Corregir detalle");
        long movimientosAntes = movimientos.count();
        DocumentoVenta copia = documentos.copiarAnulado(parconaId, adminId, original.getId(), original.getVersion());
        assertThat(copia.getDocumentoOrigenId()).isEqualTo(original.getId());
        assertThat(copia.getCodigoOrigen()).isEqualTo("F001-0001");
        assertThat(copia.getEstado()).isEqualTo(EstadoDocumentoVenta.BORRADOR);
        assertThat(copia.getSerie()).isNull();
        assertThat(copia.getCorrelativo()).isNull();
        assertThat(copia.getConfirmadoEn()).isNull();
        assertThat(copia.getAnuladoEn()).isNull();
        assertThat(copia.getCobroVigente()).isZero();
        assertThat(copia.getCliente().getId()).isEqualTo(cliente.getId());
        assertThat(copia.getClienteNombre()).isEqualTo("Empresa cliente");
        assertThat(copia.getDetalles()).isEqualTo(form.getDetalles());
        assertThat(copia.getTotal()).isEqualByComparingTo("3.50");
        assertThat(copia.getSubtotal()).isEqualByComparingTo(original.getSubtotal());
        assertThat(copia.getIgv()).isEqualByComparingTo(original.getIgv());
        assertThat(copia.getVuelto()).isEqualByComparingTo("0.50");
        assertThat(copia.getLineas().getFirst().getImporteCalculado()).isEqualByComparingTo("3.25");
        assertThat(copia.getPagos()).hasSize(3);
        assertThat(copia.getPagos().getLast().getReferencia()).isEqualTo("TR-A");
        assertThat(copia.getPagos().getLast().getBanco()).isEqualTo("BCP");
        assertThat(copia.getPagos().getLast().getRevertido()).isZero();
        assertThat(documentos.copiarAnulado(parconaId, adminId, original.getId(), original.getVersion()).getId()).isEqualTo(copia.getId());
        assertThat(movimientos.count()).isEqualTo(movimientosAntes);
        DocumentoVentaForm corregido = documentos.formulario(parconaId, copia.getId());
        corregido.setDetalles("Detalle corregido");
        copia = documentos.guardarBorrador(parconaId, adminId, corregido);
        DocumentoVenta confirmada = documentos.confirmar(parconaId, adminId, copia.getId(), copia.getVersion());
        assertThat(confirmada.getCodigoDocumento()).isEqualTo("F001-0002");
        assertThat(confirmada.getCobroVigente()).isEqualByComparingTo("3.50");
        assertThat(documentos.ver(parconaId, original.getId()).getDetalles()).isEqualTo(form.getDetalles());
        assertThat(documentos.ver(parconaId, original.getId()).getCobroVigente()).isZero();
        assertThat(inventarios.findByProductoIdAndSedeId(madera().getId(), parconaId).orElseThrow().getStock()).isEqualByComparingTo("2.75");
    }

    @Test
    void anulacionValidaEstadoVersionMotivoYRevierteTodoSiReposicionFalla() {
        DocumentoVentaForm form = ventaPagada("1", "10", "20");
        Producto melamina = productos.findAll().stream().filter(p -> p.getNombre().contains("melamina")).findFirst().orElseThrow();
        form.getLineas().add(linea(melamina.getId(), "1", "10", null));
        DocumentoVenta pendiente = documentos.guardarBorrador(parconaId, adminId, form);
        assertThatThrownBy(() -> documentos.anular(parconaId, adminId, pendiente.getId(), pendiente.getVersion(), "Error"))
                .isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> documentos.copiarAnulado(parconaId, adminId, pendiente.getId(), pendiente.getVersion()))
                .isInstanceOf(ReglaNegocioException.class);
        DocumentoVenta doc = documentos.confirmar(parconaId, adminId, pendiente.getId(), pendiente.getVersion());
        assertThatThrownBy(() -> documentos.anular(parconaId, adminId, doc.getId(), -1L, "Error")).isInstanceOf(ReglaNegocioException.class);
        for (String motivo : List.of("  ", "x".repeat(256))) {
            assertThatThrownBy(() -> documentos.anular(parconaId, adminId, doc.getId(), doc.getVersion(), motivo)).isInstanceOf(ReglaNegocioException.class);
        }
        Inventario madera = inventarios.findByProductoIdAndSedeId(madera().getId(), parconaId).orElseThrow();
        madera.setStock(new BigDecimal("99999999.9999"));
        inventarios.save(madera);
        var stocks = inventarios.findAll().stream().map(i -> i.getStock()).toList();
        long antes = movimientos.count();
        assertThatThrownBy(() -> documentos.anular(parconaId, adminId, doc.getId(), doc.getVersion(), "Error"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("máximo");
        assertThat(inventarios.findAll().stream().map(i -> i.getStock()).toList()).isEqualTo(stocks);
        assertThat(movimientos.count()).isEqualTo(antes);
        assertThat(documentos.ver(parconaId, doc.getId()).getEstado()).isEqualTo(EstadoDocumentoVenta.CONFIRMADO);
        assertThat(documentos.ver(parconaId, doc.getId()).getCobroVigente()).isEqualByComparingTo("20");
    }

    @Test
    void anulacionYCopiaWebRespetanPermisosYCsrf() throws Exception {
        DocumentoVenta doc = ventaConfirmada();
        String ruta = "/ventas/" + doc.getId();
        MockHttpSession general = login("admin");
        MockHttpSession local = login("admin.ica");
        MockHttpSession consulta = login("consulta");
        for (String accion : List.of("/anular", "/copiar")) {
            mvc.perform(post(ruta + accion).session(general).param("version", doc.getVersion().toString()).param("motivo", "Error"))
                    .andExpect(status().isForbidden());
            mvc.perform(post(ruta + accion).session(consulta).with(csrf()).param("version", doc.getVersion().toString()).param("motivo", "Error"))
                    .andExpect(status().isForbidden());
            mvc.perform(post(ruta + accion).session(local).with(csrf()).param("version", doc.getVersion().toString()).param("motivo", "Error"))
                    .andExpect(status().isNotFound());
        }
        mvc.perform(post(ruta + "/anular").session(general).with(csrf()).param("version", doc.getVersion().toString()).param("motivo", "Error <prueba>"))
                .andExpect(redirectedUrl(ruta));
        mvc.perform(get(ruta).session(general)).andExpect(status().isOk())
                .andExpect(content().string(containsString("DOCUMENTO ANULADO")))
                .andExpect(content().string(containsString("Error &lt;prueba&gt;")))
                .andExpect(content().string(containsString("Copiar datos")));
        DocumentoVenta anulada = documentos.ver(parconaId, doc.getId());
        mvc.perform(post(ruta + "/copiar").session(general).with(csrf()).param("version", anulada.getVersion().toString()))
                .andExpect(status().is3xxRedirection());
        DocumentoVenta copia = documentoRepository.findFirstByDocumentoOrigenIdAndEstadoOrderByIdDesc(doc.getId(), EstadoDocumentoVenta.BORRADOR).orElseThrow();
        mvc.perform(get("/ventas/" + copia.getId() + "/editar").session(general)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Copia de")))
                .andExpect(content().string(containsString("B001-0001")));
        mvc.perform(get("/ventas").session(general)).andExpect(status().isOk());
    }

    @Test
    void anulacionesYCopiasConcurrentesSoloCreanUnaReposicionYUnaCopiaPendiente() throws Exception {
        DocumentoVenta doc = ventaConfirmada();
        long antes = movimientos.count();
        var pool = Executors.newFixedThreadPool(2);
        var inicio = new CountDownLatch(1);
        try {
            var tarea = (java.util.concurrent.Callable<Boolean>) () -> {
                inicio.await(5, TimeUnit.SECONDS);
                try { documentos.anular(parconaId, adminId, doc.getId(), doc.getVersion(), "Error"); return true; }
                catch (ReglaNegocioException e) { return false; }
            };
            var uno = pool.submit(tarea);
            var dos = pool.submit(tarea);
            inicio.countDown();
            assertThat(List.of(uno.get(20, TimeUnit.SECONDS), dos.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
            DocumentoVenta anulada = documentos.ver(parconaId, doc.getId());
            var tareaCopia = (java.util.concurrent.Callable<Long>) () -> documentos.copiarAnulado(parconaId, adminId, doc.getId(), anulada.getVersion()).getId();
            var copia1 = pool.submit(tareaCopia);
            var copia2 = pool.submit(tareaCopia);
            assertThat(copia1.get(20, TimeUnit.SECONDS)).isEqualTo(copia2.get(20, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
        assertThat(movimientos.count()).isEqualTo(antes + 1);
        assertThat(documentoRepository.count()).isEqualTo(2);
        assertThat(inventarios.findByProductoIdAndSedeId(madera().getId(), parconaId).orElseThrow().getStock()).isEqualByComparingTo("3");
    }

    @Test
    void confirmaIgvPagosMixtosVueltoYStockUnaSolaVez() {
        Long productoId = madera().getId();
        Cliente c = clientes.guardar(empresaId, cliente("Cliente original", TipoIdentificacion.DNI, "12345678"));
        DocumentoVentaForm form = ventaPagada("1", "150", "50");
        form.setClienteId(c.getId());
        form.getPagos().add(pago(MedioPago.YAPE, "40", "YP-001", null));
        form.getPagos().add(pago(MedioPago.YAPE, "30", "YP-002", null));
        form.getPagos().add(pago(MedioPago.TRANSFERENCIA, "50", "TR-001", "BCP"));
        long antes = movimientos.count();
        DocumentoVenta doc = documentos.guardarBorrador(parconaId, adminId, form);
        assertThat(doc.getSubtotal()).isEqualByComparingTo("127.12");
        assertThat(doc.getIgv()).isEqualByComparingTo("22.88");
        assertThat(doc.getTotal()).isEqualByComparingTo("150");
        assertThat(doc.getRecibido()).isEqualByComparingTo("170");
        assertThat(doc.getVuelto()).isEqualByComparingTo("20");
        assertThat(doc.getPagos().getFirst().getAplicado()).isEqualByComparingTo("30");
        assertThat(inventarios.findByProductoIdAndSedeId(productoId, parconaId).orElseThrow().getStock()).isEqualByComparingTo("3");
        DocumentoVentaForm obsoleto = documentos.formulario(parconaId, doc.getId());
        DocumentoVenta confirmado = documentos.confirmar(parconaId, adminId, doc.getId(), doc.getVersion());
        assertThat(confirmado.getEstado()).isEqualTo(EstadoDocumentoVenta.CONFIRMADO);
        assertThat(confirmado.getCodigoDocumento()).isEqualTo("B001-0001");
        assertThat(confirmado.getConfirmadoEn()).isNotNull();
        assertThat(confirmado.getConfirmadoPor().getId()).isEqualTo(adminId);
        assertThat(inventarios.findByProductoIdAndSedeId(productoId, parconaId).orElseThrow().getStock()).isEqualByComparingTo("2");
        assertThat(inventarios.findByProductoIdAndSedeId(productoId, icaId).orElseThrow().getStock()).isEqualByComparingTo("35");
        assertThat(movimientos.count()).isEqualTo(antes + 1);
        assertThat(movimientos.findAll().stream().filter(m -> m.getMotivo().contains("B001-0001")).findFirst().orElseThrow().getCantidad())
                .isEqualByComparingTo("-1");
        assertThatThrownBy(() -> documentos.confirmar(parconaId, adminId, doc.getId(), doc.getVersion())).isInstanceOf(ReglaNegocioException.class);
        assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId, obsoleto)).isInstanceOf(ReglaNegocioException.class);
        ClienteForm editarCliente = clientes.formulario(empresaId, c.getId());
        editarCliente.setNombre("Nombre nuevo");
        clientes.guardar(empresaId, editarCliente);
        assertThat(documentos.ver(parconaId, doc.getId()).getClienteNombre()).isEqualTo("Cliente original");
        assertThat(movimientos.count()).isEqualTo(antes + 1);
    }

    @Test
    void dashboardCuentaSoloVentasConfirmadasDeLaSedeYDelPeriodo() throws Exception {
        DocumentoVenta doc = ventaConfirmada();
        documentos.guardarBorrador(parconaId, adminId, ventaPagada("1", "500", "500"));
        DocumentoVenta otraSede = documentos.guardarBorrador(icaId, adminId, ventaPagada("1", "90", "90"));
        documentos.confirmar(icaId, adminId, otraSede.getId(), otraSede.getVersion());
        var hoy = dashboard.periodo("hoy", "", "");
        var informe = dashboard.informe(parconaId, hoy);
        assertThat(informe.cantidad()).isEqualTo(1);
        assertThat(informe.total()).isEqualByComparingTo("10");
        assertThat(informe.cobros().getFirst().monto()).isEqualByComparingTo("10");
        assertThat(informe.cobros().getFirst().pagos()).isEqualTo(1);
        assertThat(informe.puntos()).hasSize(24);
        assertThat(informe.puntos().stream().map(p -> p.monto()).reduce(BigDecimal.ZERO, (total, monto) -> total.add(monto))).isEqualByComparingTo("10");
        assertThat(informe.mejores().getFirst().importe()).isEqualByComparingTo("10");
        assertThat(informe.mejores().getFirst().cantidad()).isEqualByComparingTo("1");
        var ayer = hoy.desde().minusDays(1).toString();
        assertThat(dashboard.informe(parconaId, dashboard.periodo("personalizado", ayer, ayer)).cantidad()).isZero();
        assertThat(dashboard.informe(parconaId, dashboard.periodo("personalizado", ayer, ayer)).cobros())
                .allSatisfy(c -> { assertThat(c.monto()).isZero(); assertThat(c.pagos()).isZero(); });
        var mes = dashboard.periodo("mes", "", "");
        assertThat(dashboard.informe(parconaId, mes).puntos()).hasSize(mes.desde().lengthOfMonth());
        assertThat(dashboard.informe(parconaId, dashboard.periodo("anio", "", "")).puntos()).hasSize(12);
        assertThatThrownBy(() -> dashboard.periodo("personalizado", "2026-12-01", "2026-01-01")).isInstanceOf(IllegalArgumentException.class);
        mvc.perform(get("/dashboard").session(login("admin")).param("periodo", "personalizado").param("desde", "no-fecha"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Selecciona las fechas")));
        documentos.anular(parconaId, adminId, doc.getId(), doc.getVersion(), "Prueba indicador");
        assertThat(dashboard.informe(parconaId, hoy).cantidad()).isZero();
        assertThat(dashboard.informe(parconaId, hoy).mejores()).isEmpty();
        assertThat(dashboard.informe(parconaId, hoy).cobros()).allSatisfy(c -> assertThat(c.monto()).isZero());
    }

    @Test
    void dashboardAgrupaCobrosPorBancoSinDuplicarYapeNiContarVuelto() throws Exception {
        CuentaEmpresaForm otraBcp = new CuentaEmpresaForm();
        otraBcp.setBanco("BCP"); otraBcp.setNumero("1919999999999");
        cuentas.agregar(empresaId, otraBcp);
        Long otraBcpId = cuentas.listar(empresaId).getLast().getId();
        CuentaEmpresaForm interbank = new CuentaEmpresaForm();
        interbank.setBanco("INTERBANK"); interbank.setNumero("2001234567890");
        cuentas.agregar(empresaId, interbank);
        Long interbankId = cuentas.listar(empresaId).getLast().getId();
        CuentaEmpresaForm bbva = new CuentaEmpresaForm();
        bbva.setBanco("BBVA"); bbva.setNumero("1001234567890");
        cuentas.agregar(empresaId, bbva);
        cuentas.estado(empresaId, cuentas.listar(empresaId).getLast().getId(), false);
        Empresa otraEmpresa = new Empresa(); otraEmpresa.setNombreComercial("Otra empresa"); empresas.save(otraEmpresa);
        CuentaEmpresa cuentaAjena = new CuentaEmpresa(); cuentaAjena.setEmpresa(otraEmpresa);
        cuentaAjena.setBanco("PICHINCHA"); cuentaAjena.setNumero("9001234567890"); cuentaRepository.save(cuentaAjena);

        var form = ventaPagada("1", "150", "50");
        form.getPagos().add(pago(MedioPago.EFECTIVO, "10", null, null));
        form.getPagos().add(pago(MedioPago.YAPE, "40", "YP-1", null));
        form.getPagos().add(pago(MedioPago.YAPE, "30", "YP-2", null));
        form.getPagos().add(pago(MedioPago.TRANSFERENCIA, "20", "BCP-1", "BCP"));
        var pagoOtraBcp = pago(MedioPago.TRANSFERENCIA, "10", "BCP-2", "BCP");
        pagoOtraBcp.setCuentaDestinoId(otraBcpId); form.getPagos().add(pagoOtraBcp);
        var pagoInterbank = pago(MedioPago.TRANSFERENCIA, "20", "IB-1", "INTERBANK");
        pagoInterbank.setCuentaDestinoId(interbankId); form.getPagos().add(pagoInterbank);
        var borrador = documentos.guardarBorrador(parconaId, adminId, form);
        var confirmado = documentos.confirmar(parconaId, adminId, borrador.getId(), borrador.getVersion());
        cuentas.estado(empresaId, interbankId, false);

        var hoy = dashboard.periodo("hoy", "", "");
        var informe = dashboard.informe(parconaId, hoy);
        assertThat(informe.cobros()).extracting(c -> c.nombre()).containsExactly("Efectivo", "Yape", "BCP", "INTERBANK");
        assertThat(informe.cobros()).extracting(c -> c.pagos()).containsExactly(1L, 2L, 2L, 1L);
        assertThat(informe.cobros().get(0).monto()).isEqualByComparingTo("30");
        assertThat(informe.cobros().get(1).monto()).isEqualByComparingTo("70");
        assertThat(informe.cobros().get(2).monto()).isEqualByComparingTo("30");
        assertThat(informe.cobros().get(3).monto()).isEqualByComparingTo("20");
        assertThat(informe.cobros().stream().map(c -> c.monto()).reduce(BigDecimal.ZERO, (suma, monto) -> suma.add(monto)))
                .isEqualByComparingTo(informe.total());

        String html = mvc.perform(get("/dashboard").session(login("admin")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("1911234567890"))))
                .andExpect(content().string(not(containsString("987654321"))))
                .andReturn().getResponse().getContentAsString();
        assertThat(html.split("class=\"payment-card ").length - 1).isEqualTo(4);
        for (var cobro : informe.cobros()) {
            int inicio = html.indexOf("class=\"payment-card payment-" + cobro.tema() + "\"");
            assertThat(inicio).isGreaterThanOrEqualTo(0);
            assertThat(html.substring(inicio, html.indexOf("</article>", inicio)))
                    .contains("S/ " + cobro.monto().setScale(2).toPlainString());
        }
        mvc.perform(get("/dashboard").session(login("consulta")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("S/ 70.00")));
        assertThat(dashboard.informe(icaId, hoy).cobros()).allSatisfy(c -> assertThat(c.monto()).isZero());
        var ayer = hoy.desde().minusDays(1).toString();
        assertThat(dashboard.informe(parconaId, dashboard.periodo("personalizado", ayer, ayer)).cobros())
                .extracting(c -> c.nombre()).containsExactly("Efectivo", "Yape", "BCP");
        documentos.anular(parconaId, adminId, confirmado.getId(), confirmado.getVersion(), "Prueba cobros");
        assertThat(dashboard.informe(parconaId, hoy).cobros()).allSatisfy(c -> {
            assertThat(c.monto()).isZero(); assertThat(c.pagos()).isZero();
        });
    }

    @Test
    void cuentasRespetanEmpresaPermisosYDestinoHistorico() throws Exception {
        CuentaEmpresaForm nueva = new CuentaEmpresaForm();
        nueva.setBanco("INTERBANK"); nueva.setNumero("1234567890123"); nueva.setCelularYape("987654322");
        assertThatThrownBy(() -> cuentas.agregar(empresaId, nueva)).isInstanceOf(ReglaNegocioException.class);
        nueva.setBanco("BCP");
        assertThatThrownBy(() -> cuentas.agregar(empresaId, nueva)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Yape activo");
        nueva.setCelularYape(""); nueva.setBanco("INTERBANK");
        cuentas.agregar(empresaId, nueva);
        assertThatThrownBy(() -> cuentas.agregar(empresaId, nueva)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("registrada");
        Empresa otra = new Empresa(); otra.setNombreComercial("Otra empresa"); empresas.save(otra);
        assertThatThrownBy(() -> cuentas.buscar(otra.getId(), cuentaId)).isInstanceOf(RecursoNoEncontradoException.class);
        mvc.perform(get("/configuracion").session(login("admin"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("987654321")));
        mvc.perform(post("/configuracion/cuentas").session(login("admin.ica")).with(csrf())
                .param("banco", "BCP").param("numero", "9999999999")).andExpect(status().isForbidden());
        mvc.perform(post("/configuracion/cuentas").session(login("admin")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/ventas/nuevo").session(login("admin"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("987654321"))))
                .andExpect(content().string(not(containsString("1911234567890"))));
        DocumentoVentaForm form = ventaPagada("1", "10", "10");
        form.setPagos(List.of(pago(MedioPago.YAPE, "10", "YP-CUENTA", "BANCO MANIPULADO")));
        DocumentoVenta pendiente = documentos.guardarBorrador(parconaId, adminId, form, true);
        assertThat(pendiente.getPagos().getFirst().getBanco()).isEqualTo("BCP");
        DocumentoVenta emitido = documentos.confirmar(parconaId, adminId, pendiente.getId(), pendiente.getVersion());
        cuentas.estado(empresaId, cuentaId, false);
        assertThat(documentos.ver(parconaId, emitido.getId()).getPagos().getFirst().getBanco()).isEqualTo("BCP");
        assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId, form)).isInstanceOf(ReglaNegocioException.class).hasMessageContaining("activa");
    }

    @Test
    void pagosPendientesConservanDatosPeroNoPermitenRevisarNiConfirmar() {
        DocumentoVentaForm form = ventaPagada("1", "450", "400");
        PagoVentaForm pendiente = new PagoVentaForm();
        pendiente.setMedio(MedioPago.YAPE);
        pendiente.setCuentaDestinoId(cuentaId);
        pendiente.setReferencia("OPERACION-PENDIENTE");
        form.getPagos().add(pendiente);
        DocumentoVenta doc = documentos.guardarBorrador(parconaId, adminId, form);
        assertThat(doc.getSaldo()).isEqualByComparingTo("50");
        DocumentoVentaForm editar = documentos.formulario(parconaId, doc.getId());
        assertThat(editar.getPagos().get(1).getRecibido()).isNull();
        assertThat(editar.getPagos().get(1).getReferencia()).isEqualTo("OPERACION-PENDIENTE");
        assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId, editar, true))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Completa");
        assertThatThrownBy(() -> documentos.confirmar(parconaId, adminId, doc.getId(), doc.getVersion()))
                .isInstanceOf(ReglaNegocioException.class);
        editar.getPagos().get(1).setRecibido(new BigDecimal("50"));
        editar.getPagos().get(1).setReferencia("");
        DocumentoVenta sinCodigo = documentos.guardarBorrador(parconaId, adminId, editar);
        assertThatThrownBy(() -> documentos.confirmar(parconaId, adminId, sinCodigo.getId(), sinCodigo.getVersion()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("código");
        DocumentoVentaForm completo = documentos.formulario(parconaId, doc.getId());
        completo.getPagos().get(1).setReferencia("YAPE-50");
        DocumentoVenta listo = documentos.guardarBorrador(parconaId, adminId, completo, true);
        assertThat(listo.getSaldo()).isEqualByComparingTo("0");
        assertThat(listo.getEstado()).isEqualTo(EstadoDocumentoVenta.BORRADOR);
        assertThat(correlativos.count()).isZero();
        assertThat(documentos.confirmar(parconaId, adminId, listo.getId(), listo.getVersion()).getEstado())
                .isEqualTo(EstadoDocumentoVenta.CONFIRMADO);
    }

    @Test
    void revisarDesdeWebRechazaPagoInsuficienteSinGuardarVenta() throws Exception {
        mvc.perform(post("/ventas/guardar").session(login("admin")).with(csrf())
                .param("tipo", "BOLETA").param("accion", "revisar")
                .param("lineas[0].productoId", productos.findAll().stream()
                        .filter(p -> p.getNombre().contains("tornillo")).findFirst().orElseThrow().getId().toString())
                .param("lineas[0].cantidad", "1").param("lineas[0].precio", "450")
                .param("pagos[0].medio", "EFECTIVO").param("pagos[0].recibido", "400"))
                .andExpect(status().isOk()).andExpect(view().name("venta-form"))
                .andExpect(content().string(containsString("Completa el pago")));
        assertThat(documentoRepository.count()).isZero();
    }

    @Test
    void pagosValidanReferenciasImportesYCotizacionSinCobros() {
        DocumentoVentaForm form = ventaPagada("1", "10", "10");
        for (PagoVentaForm invalido : List.of(pago(MedioPago.YAPE, "10", " ", null),
                pago(MedioPago.TRANSFERENCIA, "10", "OP", ""),
                pago(MedioPago.YAPE, "11", "OP", null), pago(MedioPago.EFECTIVO, "0", null, null),
                pago(MedioPago.EFECTIVO, "1.001", null, null))) {
            form.setPagos(List.of(invalido));
            assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId, form, true)).isInstanceOf(ReglaNegocioException.class);
        }
        form.setPagos(List.of(pago(MedioPago.YAPE, "5", "ABC", null), pago(MedioPago.YAPE, "5", " abc ", null)));
        assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId, form))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("repetidos");
        form.setPagos(List.of(pago(MedioPago.EFECTIVO, "5", null, null)));
        DocumentoVenta parcial = documentos.guardarBorrador(parconaId, adminId, form);
        assertThat(parcial.getSaldo()).isEqualByComparingTo("5");
        assertThatThrownBy(() -> documentos.confirmar(parconaId, adminId, parcial.getId(), parcial.getVersion()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Completa");
        form.setTipo(TipoDocumentoVenta.COTIZACION);
        assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId, form))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cotizaciones");
        form.setPagos(List.of());
        DocumentoVenta cotizacion = documentos.guardarBorrador(parconaId, adminId, form);
        assertThatThrownBy(() -> documentos.confirmar(parconaId, adminId, cotizacion.getId(), cotizacion.getVersion()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cotización");
        assertThat(correlativos.count()).isZero();
    }

    @Test
    void falloDeStockRevierteTodasLasSalidasYElCorrelativo() {
        Producto unidad = productos.findAll().stream().filter(p -> p.getNombre().contains("melamina")).findFirst().orElseThrow();
        DocumentoVentaForm form = ventaPagada("4", "10", "50");
        form.getLineas().add(linea(unidad.getId(), "1", "10", null));
        var stocks = inventarios.findAll().stream().map(i -> i.getStock()).toList();
        long antes = movimientos.count();
        DocumentoVenta doc = documentos.guardarBorrador(parconaId, adminId, form);
        assertThatThrownBy(() -> documentos.confirmar(parconaId, adminId, doc.getId(), doc.getVersion()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Stock insuficiente");
        assertThat(inventarios.findAll().stream().map(i -> i.getStock()).toList()).isEqualTo(stocks);
        assertThat(movimientos.count()).isEqualTo(antes);
        assertThat(correlativos.count()).isZero();
        assertThat(documentos.ver(parconaId, doc.getId()).getEstado()).isEqualTo(EstadoDocumentoVenta.BORRADOR);
        assertThat(documentos.ver(parconaId, doc.getId()).getConfirmadoEn()).isNull();
    }

    @Test
    void confirmacionWebRevisaPermisosCsrfYDocumentoInmutable() throws Exception {
        DocumentoVenta doc = documentos.guardarBorrador(parconaId, adminId, ventaPagada("0.25", "13", "5"));
        MockHttpSession sesion = login("admin");
        String ruta = "/ventas/" + doc.getId();
        mvc.perform(get(ruta).session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Confirmar venta")))
                .andExpect(content().string(containsString("Subtotal sin IGV")));
        mvc.perform(post(ruta + "/confirmar").session(sesion).param("version", doc.getVersion().toString())).andExpect(status().isForbidden());
        mvc.perform(post(ruta + "/confirmar").session(login("consulta")).with(csrf())
                .param("version", doc.getVersion().toString())).andExpect(status().isForbidden());
        mvc.perform(post(ruta + "/confirmar").session(login("admin.ica")).with(csrf())
                .param("version", doc.getVersion().toString())).andExpect(status().isNotFound());
        mvc.perform(post(ruta + "/confirmar").session(sesion).with(csrf())
                .param("version", doc.getVersion().toString())).andExpect(redirectedUrl(ruta));
        mvc.perform(get(ruta).session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("B001-0001")))
                .andExpect(content().string(not(containsString(">Confirmar venta</button>"))));
        mvc.perform(get(ruta + "/editar").session(sesion)).andExpect(redirectedUrl("/ventas"));
        assertThat(documentos.ver(parconaId, doc.getId()).getIgv()).isEqualByComparingTo("0.50");
    }

    @Test
    void confirmarRevalidaProductoSedeUsuarioYVersion() {
        DocumentoVenta doc = documentos.guardarBorrador(parconaId, adminId, ventaPagada("1", "10", "10"));
        assertThatThrownBy(() -> documentos.confirmar(parconaId, adminId, doc.getId(), -1L)).isInstanceOf(ReglaNegocioException.class);
        Producto p = madera();
        p.setEstado(false);
        productos.save(p);
        assertThatThrownBy(() -> documentos.confirmar(parconaId, adminId, doc.getId(), doc.getVersion())).isInstanceOf(ReglaNegocioException.class);
        p.setEstado(true);
        productos.save(p);
        Usuario local = usuarios.findByUsuario("admin.ica").orElseThrow();
        assertThatThrownBy(() -> documentos.confirmar(parconaId, local.getId(), doc.getId(), doc.getVersion())).isInstanceOf(ReglaNegocioException.class);
        Usuario admin = usuarios.findById(adminId).orElseThrow();
        admin.setEstado(false);
        usuarios.save(admin);
        assertThatThrownBy(() -> documentos.confirmar(parconaId, adminId, doc.getId(), doc.getVersion())).isInstanceOf(ReglaNegocioException.class);
        admin.setEstado(true);
        usuarios.save(admin);
        sedeService.cambiarEstado(empresaId, parconaId);
        assertThatThrownBy(() -> documentos.confirmar(parconaId, adminId, doc.getId(), doc.getVersion())).isInstanceOf(ReglaNegocioException.class);
        assertThat(correlativos.count()).isZero();
    }

    @Test
    void confirmacionesConcurrentesNoDuplicanSalidaNiSobrevenden() throws Exception {
        DocumentoVenta primera = documentos.guardarBorrador(parconaId, adminId, ventaPagada("2", "10", "20"));
        DocumentoVenta segunda = documentos.guardarBorrador(parconaId, adminId, ventaPagada("2", "10", "20"));
        long antes = movimientos.count();
        var inicio = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(3);
        try {
            var resultados = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (DocumentoVenta doc : List.of(primera, primera, segunda)) {
                resultados.add(pool.submit(() -> {
                    inicio.await(5, TimeUnit.SECONDS);
                    try {
                        documentos.confirmar(parconaId, adminId, doc.getId(), doc.getVersion());
                        return true;
                    } catch (ReglaNegocioException e) { return false; }
                }));
            }
            inicio.countDown();
            int confirmadas = 0;
            for (var resultado : resultados) if (resultado.get(20, TimeUnit.SECONDS)) confirmadas++;
            assertThat(confirmadas).isEqualTo(1);
        } finally { pool.shutdownNow(); }
        assertThat(movimientos.count()).isEqualTo(antes + 1);
        assertThat(inventarios.findByProductoIdAndSedeId(madera().getId(), parconaId).orElseThrow().getStock()).isEqualByComparingTo("1");
        assertThat(correlativos.findBySedeIdAndTipo(parconaId, TipoDocumentoVenta.BOLETA).orElseThrow().getUltimo()).isEqualTo(1);
    }

    @Test
    void detalleConservaCantidadAjusteYRedondeaMaderaSinMoverInventario() {
        Producto madera = madera();
        Producto clavos = productos.findAll().stream().filter(p -> "UND".equals(p.getUnidadMedida())).findFirst().orElseThrow();
        clavos.setUnidadMedida("KG");
        productos.save(clavos);
        var stocks = inventarios.findAll().stream().map(i -> i.getStock()).toList();
        long movimientosAntes = movimientos.count();
        DocumentoVentaForm form = borrador(TipoDocumentoVenta.BOLETA, null);
        form.getLineas().add(linea(madera.getId(), "16.6666", "9", null));
        form.getLineas().add(linea(clavos.getId(), "0.25", "13", "3.50"));
        DocumentoVenta doc = documentos.guardarBorrador(parconaId, adminId, form);
        assertThat(doc.getTotal()).isEqualByComparingTo("153.50");
        assertThat(doc.getLineas().getFirst().getImporteCalculado()).isEqualByComparingTo("150.00");
        DetalleVenta clavo = doc.getLineas().getLast();
        assertThat(clavo.getCantidad()).isEqualByComparingTo("0.25");
        assertThat(clavo.getImporteCalculado()).isEqualByComparingTo("3.25");
        assertThat(clavo.getImporteCobrar()).isEqualByComparingTo("3.50");
        assertThat(clavo.getAjuste()).isEqualByComparingTo("0.25");
        DocumentoVentaForm reabierto = documentos.formulario(parconaId, doc.getId());
        assertThat(reabierto.getLineas().getFirst().getImporteCobrar()).isNull();
        assertThat(reabierto.getLineas().getLast().getImporteCobrar()).isEqualByComparingTo("3.50");
        reabierto.getLineas().removeLast();
        reabierto.getLineas().getFirst().setCantidad(new BigDecimal("0.5"));
        DocumentoVenta actualizado = documentos.guardarBorrador(parconaId, adminId, reabierto);
        assertThat(actualizado.getTotal()).isEqualByComparingTo("4.50");
        assertThat(documentos.formulario(parconaId, doc.getId()).getLineas()).hasSize(1);
        assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId, reabierto))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("cambió");
        assertThat(inventarios.findAll().stream().map(i -> i.getStock()).toList()).isEqualTo(stocks);
        assertThat(movimientos.count()).isEqualTo(movimientosAntes);
        assertThat(correlativos.count()).isZero();
    }

    @Test
    void detalleValidaDecimalesUnidadesDuplicadosEImportes() {
        Long maderaId = madera().getId();
        DocumentoVentaForm form = borrador(TipoDocumentoVenta.BOLETA, null);
        for (DetalleVentaForm invalida : List.of(linea(maderaId, "0.00001", "9", null),
                linea(maderaId, "0", "9", null), linea(maderaId, "1", "-1", null),
                linea(maderaId, "1", "9", "-1"), linea(maderaId, "1", "9", "1.001"),
                linea(maderaId, "9999999999", "99999999", null))) {
            form.setLineas(List.of(invalida));
            assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId, form))
                    .isInstanceOf(ReglaNegocioException.class);
        }
        Long unidad = productos.findAll().stream().filter(p -> "UND".equals(p.getUnidadMedida())).findFirst().orElseThrow().getId();
        form.setLineas(List.of(linea(unidad, "0.5", "10", null)));
        assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId, form))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("enteras");
        form.setLineas(List.of(linea(maderaId, "1", "9", null), linea(maderaId, "2", "9", null)));
        assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId, form))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("repetido");
        assertThat(documentoRepository.count()).isZero();
    }

    @Test
    void detalleRechazaProductoAjenoALaSedeEInactivo() {
        Long triplay = productos.findAll().stream().filter(p -> p.getNombre().contains("triplay")).findFirst().orElseThrow().getId();
        DocumentoVentaForm form = borrador(TipoDocumentoVenta.BOLETA, null);
        form.getLineas().add(linea(triplay, "1", "10", null));
        assertThatThrownBy(() -> documentos.guardarBorrador(icaId, adminId, form))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("esta sede");
        Producto madera = madera();
        madera.setEstado(false);
        productos.save(madera);
        form.setLineas(List.of(linea(madera.getId(), "1", "9", null)));
        assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId, form))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("inactivos");
    }

    @Test
    void detalleWebGuardaRenderizaYNoConfiaEnTotalesEnviados() throws Exception {
        MockHttpSession sesion = login("admin");
        Long productoId = madera().getId();
        mvc.perform(post("/ventas/guardar").session(sesion).with(csrf()).param("tipo", "BOLETA")
                .param("lineas[0].productoId", productoId.toString()).param("lineas[0].cantidad", "0.25")
                .param("lineas[0].precio", "13").param("lineas[0].importeCobrar", "3.50")
                .param("total", "0.01").param("lineas[0].importeCalculado", "0.01"))
                .andExpect(redirectedUrl("/ventas"));
        DocumentoVenta doc = documentos.listar(parconaId).getFirst();
        assertThat(doc.getTotal()).isEqualByComparingTo("3.50");
        mvc.perform(get("/ventas/" + doc.getId() + "/editar").session(sesion))
                .andExpect(status().isOk()).andExpect(content().string(containsString("lineas[0].cantidad")))
                .andExpect(content().string(containsString("value=\"3.50\"")));
        mvc.perform(post("/ventas/guardar").session(sesion).with(csrf()).param("tipo", "BOLETA")
                .param("lineas[0].productoId", productoId.toString()).param("lineas[0].cantidad", "0.12345")
                .param("lineas[0].precio", "13"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("cuatro decimales")));
        assertThat(documentoRepository.count()).isEqualTo(1);
    }

    @Test
    void clienteValidaIdentificacionYEvitaDuplicadosInclusoInactivos() {
        Cliente creado = clientes.guardar(empresaId, cliente("  Ana   Perez  ", TipoIdentificacion.DNI, "12345678"));
        assertThat(creado.getNombre()).isEqualTo("Ana Perez");
        clientes.cambiarEstado(empresaId, creado.getId());
        assertThatThrownBy(() -> clientes.guardar(empresaId, cliente("Otra", TipoIdentificacion.DNI, "12345678")))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Ya existe");
        assertThatThrownBy(() -> clientes.guardar(empresaId, cliente("Otra", TipoIdentificacion.RUC, "123")))
                .isInstanceOf(ReglaNegocioException.class);
        clientes.guardar(empresaId, cliente("Cliente sin documento A", TipoIdentificacion.SIN_DOCUMENTO, ""));
        clientes.guardar(empresaId, cliente("Cliente sin documento B", TipoIdentificacion.SIN_DOCUMENTO, ""));
        assertThat(clienteRepository.count()).isEqualTo(3);
    }

    @Test
    void clienteDeOtraEmpresaNoEsVisibleNiEditable() {
        Empresa otra = new Empresa();
        otra.setNombreComercial("Otra empresa");
        otra = empresas.save(otra);
        Cliente ajeno = clientes.guardar(otra.getId(), cliente("Ajeno", TipoIdentificacion.DNI, "12345678"));
        assertThat(clientes.listar(empresaId)).isEmpty();
        assertThatThrownBy(() -> clientes.formulario(empresaId, ajeno.getId()))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId,
                borrador(TipoDocumentoVenta.BOLETA, ajeno.getId()))).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void permisosDeClientesYVentasSeAplicanEnServidor() throws Exception {
        mvc.perform(get("/clientes")).andExpect(status().is3xxRedirection());
        MockHttpSession local = login("admin.ica");
        mvc.perform(get("/clientes/nuevo").session(local)).andExpect(status().isOk());
        mvc.perform(post("/clientes/guardar").session(local).with(csrf())
                .param("nombre", "Cliente local").param("tipoIdentificacion", "DNI")
                .param("numeroDocumento", "12345678").param("empresaId", "999999"))
                .andExpect(redirectedUrl("/clientes"));
        Cliente c = clientes.listar(empresaId).getFirst();
        mvc.perform(get("/clientes/" + c.getId() + "/editar").session(local)).andExpect(status().isOk());
        mvc.perform(post("/clientes/" + c.getId() + "/estado").session(local).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/clientes/guardar").session(local)).andExpect(status().isForbidden());
        MockHttpSession consulta = login("consulta");
        for (String ruta : List.of("/clientes", "/ventas", "/ventas/nuevo")) {
            mvc.perform(get(ruta).session(consulta)).andExpect(status().isForbidden());
        }
        mvc.perform(post("/ventas/guardar").session(consulta).with(csrf())).andExpect(status().isForbidden());
        MockHttpSession general = login("admin");
        mvc.perform(post("/clientes/" + c.getId() + "/estado").session(general).with(csrf()))
                .andExpect(redirectedUrl("/clientes"));
        assertThat(clientes.buscar(empresaId, c.getId()).getEstado()).isFalse();
    }

    @Test
    void borradorWebGuardaDetallesSinStockCobrosNiNumeracion() throws Exception {
        MockHttpSession sesion = login("admin");
        long movimientosAntes = movimientos.count();
        var stocksAntes = inventarios.findAll().stream().map(i -> i.getStock()).toList();
        mvc.perform(get("/ventas/nuevo").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Detalles generales")))
                .andExpect(content().string(containsString("sin valor tributario")));
        mvc.perform(post("/ventas/guardar").session(sesion).with(csrf())
                .param("tipo", "BOLETA").param("detalles", "10 listones <medidas>")
                .param("sedeId", icaId.toString()).param("estado", "CONFIRMADO")
                .param("serie", "FORZADO").param("correlativo", "55"))
                .andExpect(redirectedUrl("/ventas"));
        DocumentoVenta doc = documentos.listar(parconaId).getFirst();
        assertThat(doc.getEstado()).isEqualTo(EstadoDocumentoVenta.BORRADOR);
        assertThat(doc.getCorrelativo()).isNull();
        assertThat(doc.getSerie()).isNull();
        assertThat(documentos.listar(icaId)).isEmpty();
        assertThat(correlativos.count()).isZero();
        assertThat(movimientos.count()).isEqualTo(movimientosAntes);
        assertThat(inventarios.findAll().stream().map(i -> i.getStock()).toList()).isEqualTo(stocksAntes);
        mvc.perform(get("/ventas/" + doc.getId() + "/editar").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("&lt;medidas&gt;")));
        mvc.perform(get("/ventas").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sin asignar")));
        mvc.perform(get("/ventas/" + doc.getId() + "/editar").session(login("admin.ica")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/ventas/guardar").session(login("admin.ica")).with(csrf())
                .param("id", doc.getId().toString()).param("version", doc.getVersion().toString())
                .param("tipo", "BOLETA").param("detalles", "Intento de otra sede"))
                .andExpect(status().isNotFound());
        assertThat(documentos.formulario(parconaId, doc.getId()).getDetalles()).isEqualTo("10 listones <medidas>");
    }

    @Test
    void facturaRequiereClienteRucActivoYNoPermiteOtraSede() {
        assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId,
                borrador(TipoDocumentoVenta.FACTURA, null))).isInstanceOf(ReglaNegocioException.class);
        Cliente c = clientes.guardar(empresaId, cliente("Empresa cliente", TipoIdentificacion.RUC, "20123456789"));
        DocumentoVenta doc = documentos.guardarBorrador(parconaId, adminId, borrador(TipoDocumentoVenta.FACTURA, c.getId()));
        assertThat(doc.getId()).isNotNull();
        clientes.cambiarEstado(empresaId, c.getId());
        assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId,
                borrador(TipoDocumentoVenta.BOLETA, c.getId()))).isInstanceOf(ReglaNegocioException.class);
        Long localId = usuarios.findByUsuario("admin.ica").orElseThrow().getId();
        assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, localId,
                borrador(TipoDocumentoVenta.BOLETA, null))).isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void edicionesObsoletasNoSobrescribenCambios() {
        Cliente c = clientes.guardar(empresaId, cliente("Cliente", TipoIdentificacion.SIN_DOCUMENTO, ""));
        ClienteForm antiguo = clientes.formulario(empresaId, c.getId());
        ClienteForm nuevo = clientes.formulario(empresaId, c.getId());
        nuevo.setNombre("Nombre corregido");
        clientes.guardar(empresaId, nuevo);
        assertThatThrownBy(() -> clientes.guardar(empresaId, antiguo)).isInstanceOf(ReglaNegocioException.class);
        DocumentoVenta doc = documentos.guardarBorrador(parconaId, adminId, borrador(TipoDocumentoVenta.BOLETA, null));
        DocumentoVentaForm anterior = documentos.formulario(parconaId, doc.getId());
        DocumentoVentaForm actual = documentos.formulario(parconaId, doc.getId());
        actual.setDetalles("Detalles corregidos");
        documentos.guardarBorrador(parconaId, adminId, actual);
        assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId, anterior))
                .isInstanceOf(ReglaNegocioException.class);
    }

    @Test
    void documentosFueraDeBorradorNoSePuedenEditar() {
        DocumentoVenta doc = documentos.guardarBorrador(parconaId, adminId, borrador(TipoDocumentoVenta.BOLETA, null));
        DocumentoVentaForm form = documentos.formulario(parconaId, doc.getId());
        for (EstadoDocumentoVenta estado : List.of(EstadoDocumentoVenta.CONFIRMADO, EstadoDocumentoVenta.ANULADO)) {
            doc = documentoRepository.findById(doc.getId()).orElseThrow();
            doc.setEstado(estado);
            documentoRepository.save(doc);
            assertThatThrownBy(() -> documentos.guardarBorrador(parconaId, adminId, form))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("borrador");
        }
    }

    @Test
    void numerosIndependientesPorSedeYTipoYReversionTransaccional() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        assertThatThrownBy(() -> tx.execute(status -> {
            numeracion.siguiente(parconaId, TipoDocumentoVenta.BOLETA);
            throw new IllegalStateException("Confirmación fallida");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(correlativos.count()).isZero();
        for (TipoDocumentoVenta tipo : TipoDocumentoVenta.values()) {
            var numero = tx.execute(status -> numeracion.siguiente(parconaId, tipo));
            assertThat(numero.correlativo()).isEqualTo(1);
            assertThat(numero.serie()).matches("[BFNC]001");
        }
        assertThat(tx.execute(status -> numeracion.siguiente(icaId, TipoDocumentoVenta.BOLETA)).correlativo()).isEqualTo(1);
        assertThat(tx.execute(status -> numeracion.siguiente(parconaId, TipoDocumentoVenta.BOLETA)).correlativo()).isEqualTo(2);
    }

    @Test
    void pantallaMuestraSerieYProximoNumeroSinReservarlo() throws Exception {
        var numeros = numeracion.proximos(parconaId);
        assertThat(numeros.get(TipoDocumentoVenta.BOLETA).getCodigo()).isEqualTo("B001-0001");
        assertThat(numeros.get(TipoDocumentoVenta.FACTURA).getCodigo()).isEqualTo("F001-0001");
        assertThat(numeros.get(TipoDocumentoVenta.NOTA_VENTA).getCodigo()).isEqualTo("N001-0001");
        assertThat(numeracion.proximos(icaId).get(TipoDocumentoVenta.BOLETA).getCodigo()).isEqualTo("B002-0001");
        MockHttpSession sesion = login("admin");
        mvc.perform(get("/ventas/nuevo").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Realizar venta")))
                .andExpect(content().string(containsString("data-serie=\"B001\"")))
                .andExpect(content().string(containsString("data-numero=\"0001\"")))
                .andExpect(content().string(containsString("Nota de venta")))
                .andExpect(content().string(containsString("action=\"/clientes/crear-desde-venta\"")))
                .andExpect(content().string(containsString("id=\"modalCliente\"")));
        mvc.perform(get("/sedes").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("B001")))
                .andExpect(content().string(containsString("B002")));
        assertThat(correlativos.count()).isZero();
    }

    @Test
    void clienteDesdeVentaDevuelveDatosYRespetaValidacionesYPermisos() throws Exception {
        MockHttpSession sesion = login("admin.ica");
        mvc.perform(post("/clientes/crear-desde-venta").session(sesion).with(csrf())
                .param("nombre", "Nuevo desde venta").param("tipoIdentificacion", "DNI")
                .param("numeroDocumento", "87654321").param("direccion", "Av. Perú 123").param("empresaId", "999999"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Nuevo desde venta"))
                .andExpect(jsonPath("$.numeroDocumento").value("87654321"))
                .andExpect(jsonPath("$.tipoIdentificacion").value("DNI"))
                .andExpect(jsonPath("$.direccion").value("Av. Perú 123"));
        mvc.perform(get("/ventas/nuevo").session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-direccion=\"Av. Perú 123\"")))
                .andExpect(content().string(containsString("data-documento=\"87654321\"")));
        assertThat(clientes.listar(empresaId)).hasSize(1);
        assertThat(documentoRepository.count()).isZero();
        mvc.perform(post("/clientes/crear-desde-venta").session(sesion).with(csrf())
                .param("nombre", "Duplicado").param("tipoIdentificacion", "DNI").param("numeroDocumento", "87654321"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores").isArray());
        mvc.perform(post("/clientes/crear-desde-venta").session(sesion).with(csrf())
                .param("nombre", "Inválido").param("tipoIdentificacion", "RUC").param("numeroDocumento", "123"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/clientes/crear-desde-venta").session(sesion).with(csrf())
                .param("id", clientes.listar(empresaId).getFirst().getId().toString()).param("nombre", "No editar aquí"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/clientes/crear-desde-venta").session(sesion)).andExpect(status().isForbidden());
        mvc.perform(post("/clientes/crear-desde-venta").session(login("consulta")).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(clienteRepository.count()).isEqualTo(1);
    }

    @Test
    void identificadorSedeEsUnicoYNoCambiaTrasUsarNumeracion() {
        SedeForm form = sedeService.formulario(empresaId, parconaId);
        form.setCodigoDocumentos(2);
        assertThatThrownBy(() -> sedeService.guardar(empresaId, form))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("otra sede");
        form.setCodigoDocumentos(3);
        sedeService.guardar(empresaId, form);
        assertThat(numeracion.proximos(parconaId).get(TipoDocumentoVenta.BOLETA).serie()).isEqualTo("B003");
        new TransactionTemplate(transactionManager).execute(status -> numeracion.siguiente(parconaId, TipoDocumentoVenta.BOLETA));
        form.setCodigoDocumentos(4);
        assertThatThrownBy(() -> sedeService.guardar(empresaId, form))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("numeración utilizada");
        assertThat(sedes.findById(parconaId).orElseThrow().getCodigoDocumentos()).isEqualTo(3);
    }

    @Test
    void numeracionConcurrenteNoDuplicaElPrimerNumero() throws Exception {
        var inicio = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tarea = (java.util.concurrent.Callable<Long>) () -> {
                inicio.await(5, TimeUnit.SECONDS);
                return new TransactionTemplate(transactionManager).execute(status ->
                        numeracion.siguiente(parconaId, TipoDocumentoVenta.BOLETA).correlativo());
            };
            var primera = pool.submit(tarea);
            var segunda = pool.submit(tarea);
            inicio.countDown();
            assertThat(List.of(primera.get(15, TimeUnit.SECONDS), segunda.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(1L, 2L);
        } finally { pool.shutdownNow(); }
    }
}
