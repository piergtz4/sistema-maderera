package sistema_maderera;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import sistema_maderera.dto.*;
import sistema_maderera.entity.*;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.*;
import sistema_maderera.service.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:permisos_sedes;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PermisosSedeIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired DemoDataService demo;
    @Autowired UsuarioGestionService gestion;
    @Autowired SedeService gestionSedes;
    @Autowired CompraService compras;
    @Autowired DocumentoVentaService ventas;
    @Autowired InventarioService inventarioService;
    @Autowired UsuarioRepository usuarios;
    @Autowired EmpresaRepository empresas;
    @Autowired SedeRepository sedes;
    @Autowired AccesoUsuarioSedeRepository accesos;
    @Autowired CompraRepository compraRepository;
    @Autowired ProveedorRepository proveedores;
    @Autowired InventarioRepository inventarios;
    @Autowired MovimientoInventarioRepository movimientos;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    private Long empresaId, generalId, norteId, surId, productoId, proveedorId;

    @BeforeEach
    void preparar() {
        compraRepository.deleteAll(); proveedores.deleteAll(); movimientos.deleteAll(); inventarios.deleteAll();
        productos.deleteAll(); categorias.deleteAll(); usuarios.deleteAll(); sedes.deleteAll(); empresas.deleteAll();
        demo.inicializar("ClaveDePrueba1");
        var general = usuarios.findByUsuario("admin").orElseThrow();
        empresaId = general.getEmpresa().getId(); generalId = general.getId();
        norteId = usuarios.findByUsuario("consulta").orElseThrow().getSede().getId();
        surId = usuarios.findByUsuario("admin.ica").orElseThrow().getSede().getId();
        productoId = productos.findAll().getFirst().getId();
        var proveedor = new Proveedor(); proveedor.setEmpresa(general.getEmpresa());
        proveedor.setNombre("Proveedor de prueba"); proveedor.setTipoIdentificacion(TipoIdentificacion.RUC);
        proveedor.setNumeroDocumento("20123456789"); proveedorId = proveedores.save(proveedor).getId();
    }

    private UsuarioForm form(String login, String rol, List<Long> consulta, List<Long> gestionIds) {
        var form = new UsuarioForm(); form.setNombre(login); form.setUsuario(login); form.setRol(rol);
        form.setSedeIds(consulta); form.setSedesGestionIds(gestionIds);
        form.setPassword("ClaveDePrueba1"); form.setConfirmarPassword("ClaveDePrueba1");
        return form;
    }

    private Usuario crear(String login, String rol, List<Long> consulta, List<Long> gestionIds) {
        return gestion.guardar(empresaId, generalId, form(login, rol, consulta, gestionIds));
    }

    private MockHttpSession login(String nombre) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf()).param("usuario", nombre)
                .param("password", "ClaveDePrueba1")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private void elegir(MockHttpSession sesion, Long sedeId) throws Exception {
        mvc.perform(post("/sede-trabajo").session(sesion).with(csrf()).param("sedeId", sedeId.toString()))
                .andExpect(redirectedUrl("/dashboard"));
    }

    private CompraForm compra(String referencia) {
        var form = new CompraForm(); form.setProveedorId(proveedorId); form.setReferencia(referencia);
        form.setFecha(compras.hoy());
        var linea = new DetalleCompraForm(); linea.setProductoId(productoId);
        linea.setCantidad(BigDecimal.ONE); linea.setPrecio(BigDecimal.TEN); form.getLineas().add(linea);
        return form;
    }

    // Guarda varias sedes con permisos diferentes y las muestra al administrador.
    @Test
    void formularioGuardaConsultaYGestionPorSede() throws Exception {
        var sesion = login("admin");
        mvc.perform(post("/usuarios/guardar").session(sesion).with(csrf())
                .param("nombre", "Contadora").param("usuario", "contadora").param("rol", "CONTADOR")
                .param("sedeIds", norteId.toString(), surId.toString()).param("sedesGestionIds", surId.toString())
                .param("password", "ClaveDePrueba1").param("confirmarPassword", "ClaveDePrueba1"))
                .andExpect(redirectedUrl("/usuarios"));
        var cuenta = usuarios.findByUsuario("contadora").orElseThrow();
        var permisos = accesos.findByUsuarioIdOrderBySedeNombreAsc(cuenta.getId());
        assertThat(permisos).hasSize(2);
        assertThat(permisos).filteredOn(a -> a.getSede().getId().equals(surId)).allMatch(AccesoUsuarioSede::isPuedeGestionar);
        assertThat(permisos).filteredOn(a -> a.getSede().getId().equals(norteId)).noneMatch(AccesoUsuarioSede::isPuedeGestionar);
        mvc.perform(get("/usuarios/{id}/editar", cuenta.getId()).session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sedes autorizadas")))
                .andExpect(content().string(containsString("name=\"sedesGestionIds\"")));
        mvc.perform(get("/usuarios").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Contador")))
                .andExpect(content().string(containsString("Gestión")));
    }

    // Una sede entra directamente; varias requieren elegir una sede autorizada.
    @Test
    void selectorSoloApareceCuandoHayVariasSedes() throws Exception {
        crear("uno", "CONTADOR", List.of(norteId), List.of());
        var uno = login("uno");
        mvc.perform(get("/dashboard").session(uno)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("class=\"change-site\""))));
        mvc.perform(get("/seleccionar-sede").session(uno)).andExpect(redirectedUrl("/dashboard"));
        crear("varios", "CONTADOR", List.of(norteId, surId), List.of(surId));
        var varios = login("varios");
        mvc.perform(get("/dashboard").session(varios)).andExpect(redirectedUrl("/seleccionar-sede"));
        mvc.perform(get("/seleccionar-sede").session(varios)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sede Parcona")))
                .andExpect(content().string(containsString("Sede Ica")));
        elegir(varios, surId);
        mvc.perform(get("/dashboard").session(varios)).andExpect(status().isOk())
                .andExpect(model().attribute("sedeId", surId))
                .andExpect(content().string(containsString("class=\"change-site\"")));
    }

    // El permiso de consulta bloquea formularios y solicitudes de modificación.
    @Test
    void contadorDeConsultaNoRegistraComprasNiProveedores() throws Exception {
        var contador = crear("lectura", "CONTADOR", List.of(norteId), List.of());
        var sesion = login("lectura");
        mvc.perform(get("/compras").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/compras/nueva\""))));
        mvc.perform(get("/proveedores").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/proveedores/nuevo\""))));
        for (String ruta : List.of("/compras/nueva", "/compras/1/editar", "/proveedores/nuevo", "/proveedores/1/editar"))
            mvc.perform(get(ruta).session(sesion)).andExpect(status().isForbidden());
        for (String ruta : List.of("/compras/guardar", "/compras/1/confirmar", "/compras/1/descartar", "/proveedores/guardar"))
            mvc.perform(post(ruta).session(sesion).with(csrf())).andExpect(status().isForbidden());
        assertThatThrownBy(() -> compras.guardar(norteId, contador.getId(), compra("LECTURA")))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("gestionar");
        assertThat(compraRepository.count()).isZero();
    }

    // El contador gestiona compras y existencias recibidas, pero no realiza ventas ni ajustes.
    @Test
    void contadorGestionaComprasSinPermisoDeVentasNiAjustesManuales() throws Exception {
        var contador = crear("contable", "CONTADOR", List.of(norteId), List.of(norteId));
        var sesion = login("contable");
        mvc.perform(get("/compras/nueva").session(sesion)).andExpect(status().isOk());
        mvc.perform(get("/proveedores/nuevo").session(sesion)).andExpect(status().isOk());
        var antes = inventarios.findByProductoIdAndSedeId(productoId, norteId).orElseThrow().getStock();
        var pendiente = compras.guardar(norteId, contador.getId(), compra("CONTADOR-1"));
        mvc.perform(get("/compras/{id}", pendiente.getId()).session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Confirmar ingreso de mercadería")));
        compras.confirmar(norteId, contador.getId(), pendiente.getId(), pendiente.getVersion());
        assertThat(inventarios.findByProductoIdAndSedeId(productoId, norteId).orElseThrow().getStock())
                .isEqualByComparingTo(antes.add(BigDecimal.ONE));
        mvc.perform(get("/ventas").session(sesion)).andExpect(status().isForbidden());
        mvc.perform(post("/ventas/1/confirmar").session(sesion).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/productos/{id}/inventario", productoId).session(sesion).with(csrf())).andExpect(status().isForbidden());
        assertThatThrownBy(() -> ventas.anular(norteId, contador.getId(), 1L, 0L, "Prueba"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("documentos");
        var movimiento = new MovimientoForm();
        movimiento.setInventarioId(inventarios.findByProductoIdAndSedeId(productoId, norteId).orElseThrow().getId());
        movimiento.setTipo(TipoMovimiento.ENTRADA); movimiento.setCantidad(BigDecimal.ONE); movimiento.setMotivo("Prueba");
        assertThatThrownBy(() -> inventarioService.mover(norteId, contador.getId(), movimiento))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("operar");
    }

    // Cambiar de sede aplica el permiso de esa sede, sin ampliar las funciones del rol.
    @Test
    void administradorTieneGestionSoloEnSedesMarcadas() throws Exception {
        var administrador = crear("multisede", "ADMINISTRADOR", List.of(norteId, surId), List.of(surId));
        var sesion = login("multisede"); elegir(sesion, norteId);
        mvc.perform(get("/ventas").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/ventas/nuevo\""))));
        mvc.perform(get("/ventas/nuevo").session(sesion)).andExpect(status().isForbidden());
        mvc.perform(post("/clientes/guardar").session(sesion).with(csrf())).andExpect(status().isForbidden());
        assertThatThrownBy(() -> compras.guardar(norteId, administrador.getId(), compra("NO-AUTORIZADO")))
                .isInstanceOf(ReglaNegocioException.class);
        elegir(sesion, surId);
        mvc.perform(get("/ventas/nuevo").session(sesion)).andExpect(status().isOk());
        mvc.perform(get("/compras/nueva").session(sesion)).andExpect(status().isOk());
        assertThat(compras.guardar(surId, administrador.getId(), compra("SUR-1")).getSede().getId()).isEqualTo(surId);
        mvc.perform(get("/usuarios").session(sesion)).andExpect(status().isForbidden());
    }

    // Ni el formulario ni la sesión pueden autorizar una sede ajena o no asignada.
    @Test
    void rechazaSedesNoAsignadasYAjenas() throws Exception {
        crear("restringido", "CONTADOR", List.of(norteId), List.of(norteId));
        var sesion = login("restringido");
        mvc.perform(post("/sede-trabajo").session(sesion).with(csrf()).param("sedeId", surId.toString()))
                .andExpect(flash().attributeExists("error"));
        sesion.setAttribute("sedeTrabajoId", surId);
        mvc.perform(get("/inventario").session(sesion)).andExpect(model().attribute("sedeId", norteId));
        var otra = new Empresa(); otra.setNombreComercial("Otra empresa"); otra = empresas.save(otra);
        var ajena = new Sede(); ajena.setEmpresa(otra); ajena.setNombre("Local ajeno"); ajena.setCodigoDocumentos(1);
        ajena.setDepartamento("Lima"); ajena.setProvincia("Lima"); ajena.setDistrito("Lima"); ajena = sedes.save(ajena);
        var invalido = form("intruso", "CONTADOR", List.of(ajena.getId()), List.of());
        assertThatThrownBy(() -> gestion.guardar(empresaId, generalId, invalido))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("tu empresa");
        assertThat(usuarios.findByUsuario("intruso")).isEmpty();
    }

    // Quitar y restaurar permisos no revive una sesión antigua.
    @Test
    void cambiarSoloPermisosInvalidaSesionAnterior() throws Exception {
        var cuenta = crear("permisos", "CONTADOR", List.of(norteId, surId), List.of(surId));
        var sesion = login("permisos"); elegir(sesion, surId);
        var editar = gestion.formulario(empresaId, cuenta.getId());
        editar.setSedesGestionIds(List.of()); gestion.guardar(empresaId, generalId, editar);
        editar.setSedesGestionIds(List.of(surId)); gestion.guardar(empresaId, generalId, editar);
        mvc.perform(get("/compras").session(sesion)).andExpect(redirectedUrl("/?sesion"));
        assertThat(sesion.isInvalid()).isTrue();
    }

    // Suspender una sede conserva la cuenta si aún tiene otra sede activa autorizada.
    @Test
    void suspenderSedeConservaAccesoAOtraAutorizadaYNoReactivaCuentas() throws Exception {
        var cuenta = crear("doslocales", "CONTADOR", List.of(norteId, surId), List.of(norteId, surId));
        var sesion = login("doslocales"); elegir(sesion, norteId);
        gestionSedes.cambiarEstado(empresaId, norteId);
        assertThat(usuarios.findById(cuenta.getId()).orElseThrow().getEstado()).isTrue();
        mvc.perform(get("/inventario").session(sesion)).andExpect(status().isOk())
                .andExpect(model().attribute("sedeId", surId));
        var soloNorte = usuarios.findByUsuario("consulta").orElseThrow();
        assertThat(soloNorte.getEstado()).isFalse();
        gestionSedes.cambiarEstado(empresaId, norteId);
        assertThat(usuarios.findById(soloNorte.getId()).orElseThrow().getEstado()).isFalse();
    }

    // Reactiva usando cualquier sede autorizada activa, aunque la primera esté suspendida.
    @Test
    void reactivaCuentaConOtraSedeAutorizadaActiva() throws Exception {
        var cuenta = crear("reactivable", "ADMINISTRADOR", List.of(norteId, surId), List.of(surId));
        gestion.cambiarEstado(empresaId, generalId, cuenta.getId());
        gestionSedes.cambiarEstado(empresaId, norteId);
        assertThat(gestion.cambiarEstado(empresaId, generalId, cuenta.getId())).isTrue();
        mvc.perform(get("/dashboard").session(login("reactivable"))).andExpect(status().isOk())
                .andExpect(model().attribute("sedeId", surId));
    }

    // Rechaza gestión sin consulta y la creación de un segundo administrador general.
    @Test
    void validaAsignacionesYAdministradorGeneralUnico() {
        assertThatThrownBy(() -> crear("invalido", "CONTADOR", List.of(norteId), List.of(surId)))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("autorizadas");
        assertThatThrownBy(() -> crear("sinlocal", "ADMINISTRADOR", List.of(), List.of()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Selecciona una sede");
        assertThatThrownBy(() -> crear("otrogeneral", "ADMINISTRADOR_GENERAL", List.of(), List.of()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya tiene un administrador general");
        var editar = gestion.formulario(empresaId, usuarios.findByUsuario("admin.ica").orElseThrow().getId());
        editar.setRol("ADMINISTRADOR_GENERAL"); editar.setSedeId(null); editar.setSedeIds(List.of()); editar.setSedesGestionIds(List.of());
        assertThatThrownBy(() -> gestion.guardar(empresaId, generalId, editar))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya tiene un administrador general");
        assertThat(usuarios.countByEmpresaIdAndRolAndEstadoTrue(empresaId, "ADMINISTRADOR_GENERAL")).isEqualTo(1);
    }
}
