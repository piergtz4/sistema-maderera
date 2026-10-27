package sistema_maderera;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import sistema_maderera.entity.Categoria;
import sistema_maderera.entity.Empresa;
import sistema_maderera.entity.Producto;
import sistema_maderera.entity.Sede;
import sistema_maderera.entity.Usuario;
import sistema_maderera.repository.CategoriaRepository;
import sistema_maderera.repository.EmpresaRepository;
import sistema_maderera.repository.ProductoRepository;
import sistema_maderera.repository.SedeRepository;
import sistema_maderera.repository.UsuarioRepository;
import sistema_maderera.repository.InventarioRepository;
import sistema_maderera.repository.MovimientoInventarioRepository;
import sistema_maderera.repository.TransferenciaRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired EmpresaRepository empresas;
    @Autowired SedeRepository sedes;
    @Autowired CategoriaRepository categorias;
    @Autowired ProductoRepository productos;
    @Autowired PasswordEncoder encoder;
    @Autowired InventarioRepository inventarios;
    @Autowired MovimientoInventarioRepository movimientos;
    @Autowired TransferenciaRepository transferencias;
    private Long categoriaId;
    private Long productoId;
    private Long sedeId;

    @BeforeEach
    void prepararDatos() {
        transferencias.deleteAll();
        movimientos.deleteAll();
        inventarios.deleteAll();
        productos.deleteAll();
        categorias.deleteAll();
        usuarios.deleteAll();
        sedes.deleteAll();
        empresas.deleteAll();
        Empresa empresa = new Empresa();
        empresa.setNombreComercial("Empresa de prueba");
        empresa = empresas.save(empresa);
        Sede sede = new Sede();
        sede.setCodigoDocumentos(1);
        sede.setEmpresa(empresa);
        sede.setNombre("Sede de prueba");
        sede.setDepartamento("Ica");
        sede.setProvincia("Ica");
        sede.setDistrito("Parcona");
        sede = sedes.save(sede);
        sedeId = sede.getId();
        crearUsuario(empresa, "admin", "ADMINISTRADOR_GENERAL", null, true);
        crearUsuario(empresa, "operador", "ADMINISTRADOR", sede, true);
        crearUsuario(empresa, "consulta", "CONSULTA", sede, true);
        crearUsuario(empresa, "inactivo", "ADMINISTRADOR", sede, false);
        Categoria categoria = new Categoria();
        categoria.setCodigo("CAT-MAD");
        categoria.setNombre("Maderas");
        categoria = categorias.save(categoria);
        categoriaId = categoria.getId();
        Producto producto = new Producto();
        producto.setCodigo("MAD-001");
        producto.setNombre("Madera de prueba");
        producto.setCategoria(categoria);
        producto.setUnidadMedida("UND");

        productoId = productos.save(producto).getId();
    }

    private void crearUsuario(Empresa empresa, String nombre, String rol, Sede sede, boolean activo) {
        Usuario usuario = new Usuario();
        usuario.setEmpresa(empresa);
        usuario.setNombre(nombre);
        usuario.setUsuario(nombre);
        usuario.setPassword(encoder.encode("ClaveDePrueba1"));
        usuario.setRol(rol);
        usuario.setSede(sede);
        usuario.setEstado(activo);
        usuarios.save(usuario);
    }

    private MockHttpSession login(String usuario) throws Exception {
        MockHttpSession sesion = (MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("usuario", usuario).param("password", "ClaveDePrueba1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andReturn().getRequest().getSession(false);
        if ("admin".equals(usuario)) {
            mvc.perform(post("/sede-trabajo").session(sesion).with(csrf())
                    .param("sedeId", sedeId.toString())).andExpect(redirectedUrl("/dashboard"));
        }
        return sesion;
    }

    @Test
    void loginRenderizaTokenYRecursosPublicos() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get("/js/login.js")).andExpect(status().isOk());
        mvc.perform(get("/css/login.css")).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/dashboard", "/inventario", "/movimientos", "/transferencias", "/categorias", "/productos",
            "/categorias/nueva", "/productos/nuevo", "/configuracion", "/seleccionar-sede"})
    void paginasProtegidasRequierenLogin(String ruta) throws Exception {
        mvc.perform(get(ruta)).andExpect(status().is3xxRedirection());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/categorias/guardar", "/productos/guardar", "/productos/1/inventario", "/movimientos", "/transferencias"})
    void anonimoNoPuedeGuardarAunqueTengaToken(String ruta) throws Exception {
        long total = categorias.count();
        mvc.perform(post(ruta).with(csrf()).param("codigo", "INTENTO").param("nombre", "Intento"))
                .andExpect(status().is3xxRedirection());
        assertThat(categorias.count()).isEqualTo(total);
        assertThat(productos.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"desconocido", "inactivo"})
    void credencialesInvalidasOUsuarioInactivoNoAcceden(String usuario) throws Exception {
        mvc.perform(post("/login").with(csrf()).param("usuario", usuario) .param("password", "ClaveDePrueba1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void passwordIncorrectoYSedeInactivaNoAcceden() throws Exception {
        mvc.perform(post("/login").with(csrf()).param("usuario", "admin").param("password", "incorrecta"))
                .andExpect(status().isUnauthorized());
        Sede sede = sedes.findFirstByOrderByIdAsc().orElseThrow();
        sede.setEstado(false);
        sedes.save(sede);
        mvc.perform(post("/login").with(csrf()).param("usuario", "operador").param("password", "ClaveDePrueba1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginRenuevaSesionYDatosProcedenDelUsuarioAutenticado() throws Exception {
        MockHttpSession previa = new MockHttpSession();
        String anterior = previa.getId();
        MockHttpSession sesion = (MockHttpSession) mvc.perform(post("/login").session(previa).with(csrf())
                .param("usuario", " admin ").param("password", "ClaveDePrueba1"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        assertThat(sesion.getId()).isNotEqualTo(anterior);
        sesion.setAttribute("sedeNombre", "Otra sede");
        mvc.perform(post("/sede-trabajo").session(sesion).with(csrf())
                .param("sedeId", sedeId.toString())).andExpect(redirectedUrl("/dashboard"));
        mvc.perform(get("/dashboard").session(sesion)).andExpect(status().isOk())
                .andExpect(model().attribute("sedeNombre", "Sede de prueba"));
    }

    @Test
    void atributoManualDeSesionNoOtorgaAcceso() throws Exception {
        MockHttpSession sesion = new MockHttpSession();
        sesion.setAttribute("usuarioId", 1L);
        mvc.perform(get("/dashboard").session(sesion)).andExpect(status().is3xxRedirection());
    }

    @Test
    void administradorPuedeGuardarYCambiarEstadoConCsrf() throws Exception {
        MockHttpSession sesion = login("admin");
        mvc.perform(post("/categorias/guardar").session(sesion).with(csrf())
                .param("codigo", "CAT-PLA").param("nombre", "Planchas")).andExpect(redirectedUrl("/categorias"));
        mvc.perform(post("/categorias/cambiar-estado/" + categoriaId).session(sesion).with(csrf()))
                .andExpect(redirectedUrl("/categorias"));
        assertThat(categorias.findById(categoriaId).orElseThrow().getEstado()).isFalse();
    }

    @Test
    void operacionesSinCsrfNoModificanDatos() throws Exception {
        MockHttpSession sesion = login("admin");
        mvc.perform(post("/categorias/guardar").session(sesion)).andExpect(status().isForbidden());
        mvc.perform(post("/productos/cambiar-estado/" + productoId).session(sesion)).andExpect(status().isForbidden());
        mvc.perform(post("/login").param("usuario", "admin").param("password", "ClaveDePrueba1"))
                .andExpect(status().isForbidden());
        assertThat(productos.findById(productoId).orElseThrow().getEstado()).isTrue();
    }

    @Test
    void consultaPuedeLeerPeroNoModificar() throws Exception {
        MockHttpSession sesion = login("consulta");
        mvc.perform(get("/categorias").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/categorias/nueva\""))));
        mvc.perform(get("/productos").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/productos/editar/"))));
        mvc.perform(get("/movimientos").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("action=\"/movimientos\""))));
        mvc.perform(post("/movimientos").session(sesion).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/transferencias").session(sesion)).andExpect(status().isForbidden());
        mvc.perform(post("/productos/" + productoId + "/inventario").session(sesion).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get("/productos/editar/" + productoId).session(sesion)).andExpect(status().isForbidden());
        mvc.perform(get("/categorias/nueva").session(sesion)).andExpect(status().isForbidden());
        mvc.perform(post("/categorias/guardar").session(sesion).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/productos/cambiar-estado/" + productoId).session(sesion).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorDeSedeEditaSuInventarioSinModificarCatalogoOEmpresa() throws Exception {
        MockHttpSession sesion = login("operador");
        mvc.perform(get("/inventario").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Registrar movimiento"))));
        mvc.perform(get("/movimientos").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Historial de movimientos")))
                .andExpect(content().string(not(containsString("action=\"/movimientos\""))));
        mvc.perform(get("/transferencias").session(sesion)).andExpect(status().isForbidden());
        mvc.perform(post("/transferencias").session(sesion).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get("/productos").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Agregar producto existente"))))
                .andExpect(content().string(containsString("Editar</a>")))
                .andExpect(content().string(not(containsString("Desactivar</button>"))));
        mvc.perform(get("/productos/editar/" + productoId).session(sesion))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/productos/" + productoId + "/inventario\"")))
                .andExpect(content().string(containsString("disabled=\"disabled\" id=\"nombre\" name=\"nombre\"")));
        mvc.perform(post("/productos/" + productoId + "/inventario").session(sesion).with(csrf())
                .param("stockInicial", "4").param("stockMinimo", "1")
                .param("nombre", "Cambio prohibido").param("sedeId", "999999"))
                .andExpect(redirectedUrl("/productos"));
        assertThat(inventarios.count()).isEqualTo(1);
        assertThat(inventarios.findByProductoIdAndSedeId(productoId, sedeId).orElseThrow().getStock())
                .isEqualByComparingTo("4");
        mvc.perform(get("/movimientos").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("action=\"/movimientos\""))));
        assertThat(productos.findById(productoId).orElseThrow().getNombre()).isEqualTo("Madera de prueba");
        mvc.perform(post("/productos/guardar").session(sesion).with(csrf())
                .param("id", productoId.toString()).param("nombre", "Cambio prohibido"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/categorias").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("href=\"/categorias/nueva\""))));
        mvc.perform(get("/categorias/nueva").session(sesion)).andExpect(status().isForbidden());
        mvc.perform(get("/configuracion").session(sesion)).andExpect(status().isForbidden());
        mvc.perform(post("/categorias/guardar").session(sesion).with(csrf())
                .param("codigo", "NO").param("nombre", "No autorizado")).andExpect(status().isForbidden());
    }

    @Test
    void vistasDelAdministradorRenderizanFormulariosProtegidos() throws Exception {
        MockHttpSession sesion = login("admin");
        for (String ruta : new String[]{"/dashboard", "/inventario", "/movimientos", "/transferencias", "/categorias", "/productos", "/configuracion",
                "/seleccionar-sede", "/categorias/nueva",
                "/productos/nuevo", "/categorias/editar/" + categoriaId, "/productos/editar/" + productoId}) {
            mvc.perform(get(ruta).session(sesion)).andExpect(status().isOk())
                    .andExpect(content().string(containsString("name=\"_csrf\"")));
        }
        mvc.perform(get("/categorias/cambiar-estado/" + categoriaId).session(sesion))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void logoutExigeCsrfEInvalidaSesion() throws Exception {
        MockHttpSession sesion = login("admin");
        mvc.perform(get("/dashboard").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/logout\"")))
                .andExpect(content().string(containsString("Cerrar sesión")));
        mvc.perform(post("/logout").session(sesion)).andExpect(status().isForbidden());
        assertThat(sesion.isInvalid()).isFalse();
        mvc.perform(post("/logout").session(sesion).with(csrf())).andExpect(redirectedUrl("/?logout"));
        assertThat(sesion.isInvalid()).isTrue();
        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
    }

    @Test
    void erroresDeFormularioSeMuestranSinGuardar() throws Exception {
        MockHttpSession sesion = login("admin");
        mvc.perform(post("/categorias/guardar").session(sesion).with(csrf())
                .param("nombre", "")).andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("categoria", "nombre"));
        mvc.perform(post("/productos/guardar").session(sesion).with(csrf())
                .param("nombre", "Prueba").param("categoriaId", categoriaId.toString())
                .param("unidadMedida", "UND").param("precioCompra", "texto"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("producto", "precioCompra"));
        assertThat(categorias.count()).isEqualTo(1);
        assertThat(productos.count()).isEqualTo(1);
    }

    @Test
    void identificadoresInexistentesMuestran404() throws Exception {
        MockHttpSession sesion = login("admin");
        mvc.perform(get("/productos/editar/999999").session(sesion)).andExpect(status().isNotFound())
                .andExpect(view().name("error/404"));
        mvc.perform(get("/categorias/editar/999999").session(sesion)).andExpect(status().isNotFound());
    }
}
