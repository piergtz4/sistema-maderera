package sistema_maderera;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:eliminacion;MODE=MySQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EliminacionIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired DemoDataService demo;
    @Autowired UsuarioGestionService gestionUsuarios;
    @Autowired SedeService gestionSedes;
    @Autowired UsuarioRepository usuarios;
    @Autowired SedeRepository sedes;
    @Autowired EmpresaRepository empresas;
    @Autowired AccesoUsuarioSedeRepository accesos;
    @Autowired CompraRepository compras;
    @Autowired DocumentoVentaRepository ventas;
    @Autowired ProveedorRepository proveedores;
    @Autowired InventarioRepository inventarios;
    @Autowired MovimientoInventarioRepository movimientos;
    @Autowired CorrelativoDocumentoRepository correlativos;
    @Autowired ProductoRepository productos;
    @Autowired CategoriaRepository categorias;
    private Usuario general;
    private Long empresaId, parconaId, icaId;
    private MockHttpSession sesion;

    @BeforeEach
    void preparar() throws Exception {
        ventas.deleteAll(); compras.deleteAll(); correlativos.deleteAll(); proveedores.deleteAll();
        movimientos.deleteAll(); inventarios.deleteAll(); productos.deleteAll(); categorias.deleteAll();
        usuarios.deleteAll(); sedes.deleteAll(); empresas.deleteAll();
        demo.inicializar("ClaveDePrueba1");
        general = usuarios.findByUsuario("admin").orElseThrow(); empresaId = general.getEmpresa().getId();
        parconaId = usuarios.findByUsuario("consulta").orElseThrow().getSede().getId();
        icaId = usuarios.findByUsuario("admin.ica").orElseThrow().getSede().getId();
        sesion = login("admin");
    }

    private MockHttpSession login(String nombre) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf()).param("usuario", nombre)
                .param("password", "ClaveDePrueba1")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private Usuario cuentaLibre(String login) {
        var form = new UsuarioForm(); form.setNombre("Usuario de prueba"); form.setUsuario(login); form.setRol("CONTADOR");
        form.setSedeIds(List.of(parconaId, icaId)); form.setSedesGestionIds(List.of(icaId));
        form.setPassword("ClaveDePrueba1"); form.setConfirmarPassword("ClaveDePrueba1");
        return gestionUsuarios.guardar(empresaId, general.getId(), form);
    }

    private Sede sedeLibre() {
        var form = new SedeForm(); form.setNombre("Local sin operaciones");
        form.setDepartamento("Ica"); form.setProvincia("Ica"); form.setDistrito("Ica");
        return gestionSedes.guardar(empresaId, form);
    }

    private void eliminar(String tipo, Long id) throws Exception {
        mvc.perform(post("/" + tipo + "/{id}/eliminar", id).session(sesion).with(csrf()).param("confirmar", "true"))
                .andExpect(redirectedUrl("/" + tipo)).andExpect(flash().attributeExists("mensaje"));
    }

    // Borra una cuenta sin operaciones y sus permisos, libera el cupo y cierra su sesión.
    @Test
    void eliminaUsuarioLibreYPermiteReutilizarSuNombre() throws Exception {
        var cuenta = cuentaLibre("libre"); var anterior = login("libre");
        var empresa = empresas.findById(empresaId).orElseThrow();
        empresa.setPlanComercial(PlanComercial.BASICO); empresa.setUsuariosAdicionales(1); empresa.setSedesAdicionales(1); empresas.saveAndFlush(empresa);
        assertThat(accesos.findByUsuarioIdOrderBySedeNombreAsc(cuenta.getId())).hasSize(2);
        mvc.perform(get("/usuarios").session(sesion)).andExpect(content().string(containsString("/usuarios/" + cuenta.getId() + "/eliminar")));
        mvc.perform(get("/usuarios/{id}/eliminar", cuenta.getId()).session(sesion)).andExpect(status().isOk())
                .andExpect(view().name("eliminar-registro")).andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("name=\"confirmar\"")))
                .andExpect(content().string(not(containsString(cuenta.getPassword()))));
        assertThat(usuarios.existsById(cuenta.getId())).isTrue();
        eliminar("usuarios", cuenta.getId());
        assertThat(usuarios.existsById(cuenta.getId())).isFalse();
        assertThat(accesos.findByUsuarioIdOrderBySedeNombreAsc(cuenta.getId())).isEmpty();
        assertThat(usuarios.countByEmpresaIdAndEstadoTrue(empresaId)).isEqualTo(3);
        mvc.perform(get("/inventario").session(anterior)).andExpect(redirectedUrl("/?sesion"));
        mvc.perform(post("/login").with(csrf()).param("usuario", "libre").param("password", "ClaveDePrueba1"))
                .andExpect(status().isUnauthorized());
        assertThat(cuentaLibre("libre").getId()).isNotEqualTo(cuenta.getId());
    }

    // Borra una sede vacía y limpia una selección de sesión que apuntaba a ella.
    @Test
    void eliminaSedeLibreYConservaLasOtrasSedes() throws Exception {
        var sede = sedeLibre(); long registros = sedes.count();
        mvc.perform(get("/sedes/{id}/eliminar", sede.getId()).session(sesion)).andExpect(status().isOk())
                .andExpect(view().name("eliminar-registro")).andExpect(content().string(containsString(sede.getNombre())));
        mvc.perform(post("/sede-trabajo").session(sesion).with(csrf()).param("sedeId", sede.getId().toString()))
                .andExpect(redirectedUrl("/dashboard"));
        eliminar("sedes", sede.getId());
        assertThat(sedes.existsById(sede.getId())).isFalse(); assertThat(sedes.count()).isEqualTo(registros - 1);
        assertThat(sedes.existsById(parconaId)).isTrue(); assertThat(sedes.existsById(icaId)).isTrue();
        mvc.perform(get("/dashboard").session(sesion)).andExpect(redirectedUrl("/seleccionar-sede"));
        assertThat(sesion.getAttribute("sedeTrabajoId")).isNull();
    }

    // Exige confirmación y token de seguridad antes de borrar sedes o usuarios.
    @Test
    void rechazaEliminacionSinConfirmacionONoAutenticada() throws Exception {
        var cuenta = cuentaLibre("confirmacion"); var sede = sedeLibre();
        for (var destino : List.of("/usuarios/" + cuenta.getId(), "/sedes/" + sede.getId())) {
            mvc.perform(post(destino + "/eliminar").session(sesion).with(csrf()))
                    .andExpect(flash().attributeExists("error"));
            mvc.perform(post(destino + "/eliminar").session(sesion).param("confirmar", "true"))
                    .andExpect(status().isForbidden());
            mvc.perform(post(destino + "/eliminar").with(csrf()).param("confirmar", "true"))
                    .andExpect(status().is3xxRedirection());
        }
        assertThat(usuarios.existsById(cuenta.getId())).isTrue(); assertThat(sedes.existsById(sede.getId())).isTrue();
    }

    // Solo el administrador general puede abrir y ejecutar la eliminación.
    @ParameterizedTest
    @ValueSource(strings = {"admin.ica", "consulta"})
    void otrosRolesNoPuedenEliminar(String login) throws Exception {
        var cuenta = cuentaLibre("objetivo"); var sede = sedeLibre(); var local = login(login);
        for (var ruta : List.of("/usuarios/" + cuenta.getId() + "/eliminar", "/sedes/" + sede.getId() + "/eliminar")) {
            mvc.perform(get(ruta).session(local)).andExpect(status().isForbidden());
            mvc.perform(post(ruta).session(local).with(csrf()).param("confirmar", "true")).andExpect(status().isForbidden());
        }
        var actor = usuarios.findByUsuario(login).orElseThrow();
        assertThatThrownBy(() -> gestionUsuarios.eliminar(empresaId, actor.getId(), cuenta.getId(), true))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("administrador general");
        assertThatThrownBy(() -> gestionSedes.eliminar(empresaId, actor.getId(), sede.getId(), true))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("administrador general");
        assertThat(usuarios.existsById(cuenta.getId())).isTrue(); assertThat(sedes.existsById(sede.getId())).isTrue();
    }

    // Protege la cuenta propia y la última sede activa de la empresa.
    @Test
    void protegeCuentaPropiaYUltimaSede() throws Exception {
        mvc.perform(post("/usuarios/{id}/eliminar", general.getId()).session(sesion).with(csrf()).param("confirmar", "true"))
                .andExpect(flash().attribute("error", "No puedes eliminar tu propia cuenta."));
        gestionSedes.cambiarEstado(empresaId, icaId);
        mvc.perform(post("/sedes/{id}/eliminar", parconaId).session(sesion).with(csrf()).param("confirmar", "true"))
                .andExpect(flash().attribute("error", "No puedes eliminar la última sede activa de la empresa."));
        assertThat(usuarios.existsById(general.getId())).isTrue(); assertThat(sedes.existsById(parconaId)).isTrue();
    }

    // Rechaza eliminar registros de otra empresa aunque se cambie la dirección manualmente.
    @Test
    void aislamientoEntreEmpresas() throws Exception {
        var otra = new Empresa(); otra.setNombreComercial("Empresa ajena"); empresas.saveAndFlush(otra);
        var sede = new Sede(); sede.setEmpresa(otra); sede.setCodigoDocumentos(1); sede.setNombre("Local ajeno");
        sede.setDepartamento("Ica"); sede.setProvincia("Ica"); sede.setDistrito("Ica"); sedes.saveAndFlush(sede);
        var cuenta = new Usuario(); cuenta.setEmpresa(otra); cuenta.setSede(sede); cuenta.setNombre("Cuenta ajena");
        cuenta.setUsuario("ajeno"); cuenta.setRol("CONTADOR"); cuenta.setPassword(general.getPassword()); usuarios.saveAndFlush(cuenta);
        for (var ruta : List.of("/usuarios/" + cuenta.getId() + "/eliminar", "/sedes/" + sede.getId() + "/eliminar")) {
            mvc.perform(get(ruta).session(sesion)).andExpect(status().isNotFound());
            mvc.perform(post(ruta).session(sesion).with(csrf()).param("confirmar", "true")).andExpect(status().isNotFound());
        }
        assertThat(usuarios.existsById(cuenta.getId())).isTrue(); assertThat(sedes.existsById(sede.getId())).isTrue();
    }

    // Conserva las operaciones y sus responsables cuando el usuario tiene historial.
    @ParameterizedTest
    @ValueSource(strings = {"MOVIMIENTO", "COMPRA", "CONFIRMA_COMPRA", "VENTA", "CONFIRMA_VENTA", "ANULA_VENTA"})
    void usuarioConRegistrosNoSeElimina(String referencia) throws Exception {
        Usuario cuenta = referencia.equals("MOVIMIENTO") ? usuarios.findByUsuario("admin.ica").orElseThrow() : cuentaLibre("con.historial");
        if (referencia.contains("COMPRA")) registrarCompra(sedes.findById(parconaId).orElseThrow(), cuenta, referencia.equals("CONFIRMA_COMPRA"));
        else if (referencia.contains("VENTA")) registrarVenta(sedes.findById(parconaId).orElseThrow(), cuenta, referencia);
        long movimientosAntes = movimientos.count(), comprasAntes = compras.count(), ventasAntes = ventas.count();
        mvc.perform(get("/usuarios/{id}/eliminar", cuenta.getId()).session(sesion)).andExpect(redirectedUrl("/usuarios"))
                .andExpect(flash().attribute("error", containsString("asociados")));
        mvc.perform(post("/usuarios/{id}/eliminar", cuenta.getId()).session(sesion).with(csrf()).param("confirmar", "true"))
                .andExpect(flash().attribute("error", containsString("asociados")));
        assertThat(usuarios.existsById(cuenta.getId())).isTrue();
        assertThat(movimientos.count()).isEqualTo(movimientosAntes); assertThat(compras.count()).isEqualTo(comprasAntes);
        assertThat(ventas.count()).isEqualTo(ventasAntes);
    }

    // Bloquea cada vínculo de una sede, incluso inventario sin stock y permisos secundarios.
    @ParameterizedTest
    @ValueSource(strings = {"INVENTARIO", "COMPRA", "VENTA", "NUMERACION", "USUARIO", "PERMISO"})
    void sedeConRegistrosNoSeElimina(String referencia) throws Exception {
        var sede = sedeLibre();
        switch (referencia) {
            case "INVENTARIO" -> {
                var inventario = new Inventario(); inventario.setSede(sede); inventario.setProducto(productos.findAll().getFirst());
                inventarios.saveAndFlush(inventario);
            }
            case "COMPRA" -> registrarCompra(sede, general, false);
            case "VENTA" -> registrarVenta(sede, general, "VENTA");
            case "NUMERACION" -> {
                var correlativo = new CorrelativoDocumento(); correlativo.setSede(sede); correlativo.setTipo(TipoDocumentoVenta.FACTURA);
                correlativos.saveAndFlush(correlativo);
            }
            case "USUARIO" -> {
                var cuenta = usuarios.findByUsuario("consulta").orElseThrow(); cuenta.setSede(sede); usuarios.saveAndFlush(cuenta);
            }
            case "PERMISO" -> {
                var permiso = new AccesoUsuarioSede(); permiso.setUsuario(usuarios.findByUsuario("consulta").orElseThrow());
                permiso.setSede(sede); accesos.saveAndFlush(permiso);
            }
        }
        mvc.perform(post("/sedes/{id}/eliminar", sede.getId()).session(sesion).with(csrf()).param("confirmar", "true"))
                .andExpect(flash().attribute("error", containsString("No puedes eliminar esta sede")));
        assertThat(sedes.existsById(sede.getId())).isTrue();
    }

    private void registrarCompra(Sede sede, Usuario responsable, boolean confirma) {
        var proveedor = new Proveedor(); proveedor.setEmpresa(general.getEmpresa()); proveedor.setNombre("Proveedor de prueba");
        proveedores.saveAndFlush(proveedor);
        var compra = new Compra(); compra.setSede(sede); compra.setProveedor(proveedor); compra.setProveedorNombre(proveedor.getNombre());
        compra.setUsuario(confirma ? general : responsable); compra.setTipo(TipoComprobanteCompra.FACTURA);
        compra.setReferencia("F001-1"); compra.setFecha(LocalDate.now()); compra.setActualizado(LocalDateTime.now());
        if (confirma) { compra.setConfirmadoPor(responsable); compra.setEstado(EstadoCompra.CONFIRMADA); compra.setConfirmadoEn(LocalDateTime.now()); }
        compras.saveAndFlush(compra);
    }

    private void registrarVenta(Sede sede, Usuario responsable, String referencia) {
        var venta = new DocumentoVenta(); venta.setSede(sede); venta.setUsuario(referencia.equals("VENTA") ? responsable : general);
        venta.setFecha(LocalDateTime.now()); venta.setActualizado(LocalDateTime.now());
        if (referencia.equals("CONFIRMA_VENTA")) { venta.setConfirmadoPor(responsable); venta.setConfirmadoEn(LocalDateTime.now()); venta.setEstado(EstadoDocumentoVenta.CONFIRMADO); }
        if (referencia.equals("ANULA_VENTA")) { venta.setAnuladoPor(responsable); venta.setAnuladoEn(LocalDateTime.now()); venta.setEstado(EstadoDocumentoVenta.ANULADO); }
        ventas.saveAndFlush(venta);
    }
}
