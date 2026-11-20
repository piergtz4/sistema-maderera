package sistema_maderera;

import jakarta.persistence.EntityManager;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.PlataformaEmpresaForm;
import sistema_maderera.entity.*;
import sistema_maderera.repository.*;
import sistema_maderera.security.AdministradorPlataformaPrincipal;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.PlataformaAdministradorEmpresaService;
import sistema_maderera.service.PlataformaAdministradorEmpresaService.UsuarioVista;
import sistema_maderera.service.PlataformaEmpresaService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlataformaAdministradorEmpresaIntegrationTests {
    private static final String CLAVE = "ClaveEmpresaPrueba123";
    private static final String CLAVE_PLATAFORMA = "ClavePlataformaPrueba123";
    private static final String NUEVA = "ClaveRenovadaPrueba456";
    @Autowired MockMvc mvc;
    @Autowired PlataformaEmpresaService empresasService;
    @Autowired PlataformaAdministradorEmpresaService servicio;
    @Autowired EmpresaRepository empresas;
    @Autowired UsuarioRepository usuarios;
    @Autowired SedeRepository sedes;
    @Autowired AdministradorPlataformaRepository plataforma;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager entityManager;
    private Empresa empresaA;
    private Empresa empresaB;
    private Usuario administrador;
    private Usuario segundo;
    private Usuario ajeno;
    private Usuario local;
    private AdministradorPlataformaPrincipal actor;

    @BeforeEach
    void preparar() {
        empresaA = crearEmpresa("Empresa administrada A", "20987654321", "empresa.a.admin");
        empresaB = crearEmpresa("Empresa administrada B", "20111222333", "empresa.b.admin");
        administrador = usuarios.findByUsuarioIgnoreCase("empresa.a.admin").orElseThrow();
        ajeno = usuarios.findByUsuarioIgnoreCase("empresa.b.admin").orElseThrow();
        segundo = cuenta("empresa.a.segundo", "Segundo administrador", "ADMINISTRADOR_GENERAL");
        local = cuenta("empresa.a.local", "Administrador de sede", "ADMINISTRADOR");
        var proveedor = new AdministradorPlataforma();
        proveedor.setNombre("Responsable de plataforma");
        proveedor.setUsuario("gestion.plataforma");
        proveedor.setPassword(encoder.encode(CLAVE_PLATAFORMA));
        actor = new AdministradorPlataformaPrincipal(plataforma.save(proveedor));
    }

    private Empresa crearEmpresa(String nombre, String ruc, String usuario) {
        var form = new PlataformaEmpresaForm();
        form.getEmpresa().setNombreComercial(nombre);
        form.getEmpresa().setRuc(ruc);
        form.getSede().setNombre("Sede de " + nombre);
        form.getSede().setDepartamento("Lima");
        form.getSede().setProvincia("Lima");
        form.getSede().setDistrito("Ate");
        form.setNombreAdministrador("Responsable de " + nombre);
        form.setUsuario(usuario);
        form.setPassword(CLAVE);
        form.setConfirmarPassword(CLAVE);
        return empresasService.crear(form);
    }

    private Usuario cuenta(String login, String nombre, String rol) {
        var usuario = new Usuario();
        usuario.setEmpresa(empresaA);
        usuario.setUsuario(login);
        usuario.setNombre(nombre);
        usuario.setRol(rol);
        usuario.setPassword(encoder.encode(CLAVE));
        if (!"ADMINISTRADOR_GENERAL".equals(rol)) {
            usuario.setSede(sedes.findByEmpresaIdAndCodigoDocumentos(empresaA.getId(), 1).orElseThrow());
        }
        return usuarios.save(usuario);
    }

    private String base() {
        return "/plataforma/empresas/" + empresaA.getId();
    }

    private String ruta(Long id) {
        return base() + "/administradores/" + id;
    }

    private MockHttpServletRequestBuilder edicion(Long id, String nombre, String login) {
        return post(ruta(id)).param("nombre", nombre).param("usuario", login);
    }

    private MockHttpServletRequestBuilder clave(Long id, String nueva, String confirmacion, String propia) {
        return post(ruta(id) + "/clave").param("nueva", nueva)
                .param("confirmacion", confirmacion).param("clavePlataforma", propia);
    }

    private MockHttpSession login(String nombre, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("usuario", nombre).param("password", password))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andReturn().getRequest().getSession(false);
    }

    // La ficha muestra las cuentas y sedes de la empresa sin exponer contraseñas.
    @Test
    void muestraDetalleSinMezclarEmpresasNiExponerClaves() throws Exception {
        mvc.perform(get(base()).with(user(actor))).andExpect(status().isOk())
                .andExpect(view().name("plataforma/empresa-detalle"))
                .andExpect(model().attribute("administradores", hasSize(2)))
                .andExpect(model().attribute("usuariosEmpresa", hasSize(3)))
                .andExpect(model().attribute("sedes", hasSize(1)))
                .andExpect(content().string(containsString("empresa.a.admin")))
                .andExpect(content().string(containsString("empresa.a.segundo")))
                .andExpect(content().string(not(containsString("empresa.b.admin"))))
                .andExpect(content().string(not(containsString("Empresa administrada B"))))
                .andExpect(content().string(containsString("empresa.a.local")))
                .andExpect(content().string(not(containsString(administrador.getPassword()))))
                .andExpect(content().string(not(containsString(CLAVE))));
        mvc.perform(get("/plataforma").with(user(actor))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Administrar")))
                .andExpect(content().string(containsString(base())));
        mvc.perform(get(ruta(administrador.getId()) + "/editar").with(user(actor)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(get(ruta(administrador.getId()) + "/clave").with(user(actor)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"clavePlataforma\"")))
                .andExpect(content().string(not(containsString(administrador.getPassword()))));
    }

    // Incluye los tres roles y las cuentas inactivas, con su sede y estado.
    @Test
    void muestraTodosLosRolesConSuSedeYEstado() throws Exception {
        var consulta = cuenta("empresa.a.consulta", "Consulta de prueba", "CONSULTA");
        consulta.setEstado(false);
        mvc.perform(get(base()).with(user(actor))).andExpect(status().isOk())
                .andExpect(model().attribute("usuariosEmpresa", hasSize(4)))
                .andExpect(model().attribute("usuariosEmpresa", hasItem(new UsuarioVista(
                        "Consulta de prueba", "empresa.a.consulta", "Consulta", "Sede de Empresa administrada A", false))))
                .andExpect(model().attribute("usuariosEmpresa", hasItem(new UsuarioVista(
                        administrador.getNombre(), "empresa.a.admin", "Administrador general", "Sin sede fija", true))))
                .andExpect(model().attribute("usuariosEmpresa", hasItem(new UsuarioVista(
                        local.getNombre(), "empresa.a.local", "Administrador de sede", "Sede de Empresa administrada A", true))))
                .andExpect(content().string(containsString("Inactivo")))
                .andExpect(content().string(not(containsString("empresa.b.admin"))))
                .andExpect(content().string(not(containsString(consulta.getPassword()))));
    }

    // Edita nombre y usuario sin aceptar cambios de rol, empresa, estado o clave.
    @Test
    void editaSoloDatosPermitidosEInvalidaLaSesionAnterior() throws Exception {
        String hash = administrador.getPassword();
        MockHttpSession sesion = login(administrador.getUsuario(), CLAVE);
        mvc.perform(edicion(administrador.getId(), "  Responsable   actualizado  ", "Empresa.A.Actualizada")
                .with(user(actor)).with(csrf()).param("rol", "ADMINISTRADOR_PLATAFORMA")
                .param("empresaId", empresaB.getId().toString()).param("id", ajeno.getId().toString())
                .param("estado", "false").param("password", NUEVA))
                .andExpect(redirectedUrl(base() + "#administradores"));
        entityManager.flush();
        entityManager.clear();
        var guardado = usuarios.findById(administrador.getId()).orElseThrow();
        assertThat(guardado.getNombre()).isEqualTo("Responsable actualizado");
        assertThat(guardado.getUsuario()).isEqualTo("empresa.a.actualizada");
        assertThat(guardado.getEmpresa().getId()).isEqualTo(empresaA.getId());
        assertThat(guardado.getRol()).isEqualTo("ADMINISTRADOR_GENERAL");
        assertThat(guardado.getEstado()).isTrue();
        assertThat(guardado.getPassword()).isEqualTo(hash);
        assertThat(usuarios.findById(ajeno.getId()).orElseThrow().getUsuario()).isEqualTo("empresa.b.admin");
        mvc.perform(get("/productos").session(sesion)).andExpect(redirectedUrl("/?sesion"));
    }

    // Rechaza nombres de usuario repetidos sin modificar la cuenta.
    @Test
    void rechazaUsuarioRepetido() throws Exception {
        mvc.perform(edicion(administrador.getId(), "Nombre rechazado", "EMPRESA.B.ADMIN").with(user(actor)).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().hasErrors())
                .andExpect(content().string(containsString("Ese nombre de usuario ya está registrado.")));
        assertThat(usuarios.findById(administrador.getId()).orElseThrow().getUsuario()).isEqualTo("empresa.a.admin");
        assertThat(usuarios.findById(administrador.getId()).orElseThrow().getNombre()).isNotEqualTo("Nombre rechazado");
    }

    // Valida los datos del formulario de edición antes de guardar.
    @Test
    void validaLosCamposDeEdicion() throws Exception {
        mvc.perform(edicion(administrador.getId(), "", "usuario con espacios").with(user(actor)).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("plataforma/administrador-editar"))
                .andExpect(model().attributeHasFieldErrors("cuenta", "nombre", "usuario"));
    }

    // Rechaza administradores de otra empresa en todas las acciones.
    @Test
    void rechazaAdministradorAjeno() throws Exception {
        mvc.perform(get(ruta(ajeno.getId()) + "/editar").with(user(actor))).andExpect(status().isNotFound());
        mvc.perform(get(ruta(ajeno.getId()) + "/clave").with(user(actor))).andExpect(status().isNotFound());
        mvc.perform(edicion(ajeno.getId(), "Intrusión", "intrusion").with(user(actor)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post(ruta(ajeno.getId()) + "/estado").with(user(actor)).with(csrf()).param("activo", "false"))
                .andExpect(status().isNotFound());
        mvc.perform(clave(ajeno.getId(), NUEVA, NUEVA, CLAVE_PLATAFORMA).with(user(actor)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(usuarios.findById(ajeno.getId()).orElseThrow().getEstado()).isTrue();
        assertThat(encoder.matches(CLAVE, usuarios.findById(ajeno.getId()).orElseThrow().getPassword())).isTrue();
    }

    // Estas acciones no permiten convertir cuentas de sede en administradores generales.
    @Test
    void rechazaCuentasQueNoSonAdministradoresGenerales() throws Exception {
        mvc.perform(get(ruta(local.getId()) + "/editar").with(user(actor))).andExpect(status().isNotFound());
        mvc.perform(edicion(local.getId(), "Cambio prohibido", "cambio.prohibido").with(user(actor)).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post(ruta(local.getId()) + "/estado").with(user(actor)).with(csrf()).param("activo", "false"))
                .andExpect(status().isNotFound());
        mvc.perform(clave(local.getId(), NUEVA, NUEVA, CLAVE_PLATAFORMA).with(user(actor)).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(usuarios.findById(local.getId()).orElseThrow().getRol()).isEqualTo("ADMINISTRADOR");
    }

    // Suspender una cuenta no cambia el acceso de los demás administradores.
    @Test
    void suspendeSoloLaCuentaElegida() throws Exception {
        MockHttpSession anterior = login(administrador.getUsuario(), CLAVE);
        MockHttpSession otra = login(segundo.getUsuario(), CLAVE);
        mvc.perform(post(ruta(administrador.getId()) + "/estado").with(user(actor)).with(csrf()).param("activo", "false"))
                .andExpect(redirectedUrl(base() + "#administradores"));
        mvc.perform(get("/productos").session(anterior)).andExpect(redirectedUrl("/?sesion"));
        mvc.perform(get("/productos").session(otra)).andExpect(status().isOk());
        mvc.perform(post("/login").with(csrf()).param("usuario", administrador.getUsuario()).param("password", CLAVE))
                .andExpect(status().isUnauthorized());
        assertThat(empresas.findById(empresaA.getId()).orElseThrow().getEstado()).isTrue();
    }

    // Activar exige un nuevo ingreso y no recupera sesiones previas a la suspensión.
    @Test
    void activaSinRecuperarSesionesAntiguas() throws Exception {
        MockHttpSession anterior = login(administrador.getUsuario(), CLAVE);
        servicio.cambiarEstado(empresaA.getId(), administrador.getId(), actor.getId(), false);
        mvc.perform(post(ruta(administrador.getId()) + "/estado").with(user(actor)).with(csrf()).param("activo", "true"))
                .andExpect(redirectedUrl(base() + "#administradores"));
        mvc.perform(get("/productos").session(anterior)).andExpect(redirectedUrl("/?sesion"));
        MockHttpSession nueva = login(administrador.getUsuario(), CLAVE);
        servicio.cambiarEstado(empresaA.getId(), administrador.getId(), actor.getId(), true);
        mvc.perform(get("/productos").session(nueva)).andExpect(status().isOk());
        assertThat(usuarios.findById(administrador.getId()).orElseThrow().getVersionAcceso()).isEqualTo(2L);
    }

    // Impide suspender al último administrador general activo.
    @Test
    void conservaAlMenosUnAdministradorActivo() throws Exception {
        servicio.cambiarEstado(empresaA.getId(), administrador.getId(), actor.getId(), false);
        mvc.perform(post(ruta(segundo.getId()) + "/estado").with(user(actor)).with(csrf()).param("activo", "false"))
                .andExpect(redirectedUrl(base() + "#administradores"))
                .andExpect(flash().attribute("error", "Debe quedar al menos un administrador general activo."));
        assertThat(usuarios.findById(segundo.getId()).orElseThrow().getEstado()).isTrue();
        mvc.perform(get(base()).with(user(actor))).andExpect(status().isOk())
                .andExpect(model().attribute("administradoresActivos", 1L));
    }

    // Restablecer cambia únicamente la clave elegida e invalida sus sesiones.
    @Test
    void restableceConLaClaveDelAdministradorDePlataforma() throws Exception {
        MockHttpSession anterior = login(administrador.getUsuario(), CLAVE);
        MockHttpSession otra = login(segundo.getUsuario(), CLAVE);
        var respuesta = mvc.perform(clave(administrador.getId(), NUEVA, NUEVA, CLAVE_PLATAFORMA).with(user(actor)).with(csrf()))
                .andExpect(redirectedUrl(base() + "#administradores")).andReturn();
        assertThat(respuesta.getFlashMap().toString()).doesNotContain(NUEVA, CLAVE_PLATAFORMA);
        entityManager.flush();
        entityManager.clear();
        var guardado = usuarios.findById(administrador.getId()).orElseThrow();
        assertThat(encoder.matches(NUEVA, guardado.getPassword())).isTrue();
        assertThat(encoder.matches(CLAVE, guardado.getPassword())).isFalse();
        assertThat(encoder.matches(CLAVE_PLATAFORMA, plataforma.findById(actor.getId()).orElseThrow().getPassword())).isTrue();
        mvc.perform(get("/productos").session(anterior)).andExpect(redirectedUrl("/?sesion"));
        mvc.perform(get("/productos").session(otra)).andExpect(status().isOk());
        mvc.perform(post("/login").with(csrf()).param("usuario", administrador.getUsuario()).param("password", CLAVE))
                .andExpect(status().isUnauthorized());
        login(administrador.getUsuario(), NUEVA);
    }

    // Una clave de plataforma incorrecta no cambia la contraseña del cliente.
    @Test
    void rechazaClaveDePlataformaIncorrectaSinMostrarContrasenas() throws Exception {
        String incorrecta = "ClaveIncorrectaDePrueba1";
        mvc.perform(clave(administrador.getId(), NUEVA, NUEVA, incorrecta).with(user(actor)).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("plataforma/administrador-clave"))
                .andExpect(model().hasErrors())
                .andExpect(content().string(containsString("Tu contraseña de administrador de plataforma no es correcta.")))
                .andExpect(content().string(not(containsString(NUEVA))))
                .andExpect(content().string(not(containsString(incorrecta))))
                .andExpect(content().string(not(containsString(administrador.getPassword()))));
        assertThat(encoder.matches(CLAVE, usuarios.findById(administrador.getId()).orElseThrow().getPassword())).isTrue();
    }

    // El restablecimiento exige la contraseña de quien administra la plataforma.
    @Test
    void exigeClaveDePlataforma() throws Exception {
        mvc.perform(clave(administrador.getId(), NUEVA, NUEVA, "").with(user(actor)).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("clave", "clavePlataforma"))
                .andExpect(content().string(not(containsString(NUEVA))));
    }

    static Stream<Arguments> clavesInvalidas() {
        return Stream.of(Arguments.of("Corta123", "Corta123", "12"),
                Arguments.of("SoloLetrasSinNumeros", "SoloLetrasSinNumeros", "letras y números"),
                Arguments.of(NUEVA, "OtraClavePrueba456", "no coinciden"),
                Arguments.of("á".repeat(36) + "1", "á".repeat(36) + "1", "demasiado larga"),
                Arguments.of(CLAVE, CLAVE, "diferente de la actual"));
    }

    // Rechaza claves débiles, repetidas, demasiado largas o sin confirmación válida.
    @ParameterizedTest
    @MethodSource("clavesInvalidas")
    void validaLaNuevaContrasena(String nueva, String confirmacion, String mensaje) throws Exception {
        mvc.perform(clave(administrador.getId(), nueva, confirmacion, CLAVE_PLATAFORMA).with(user(actor)).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().hasErrors())
                .andExpect(content().string(containsString(mensaje)))
                .andExpect(content().string(not(containsString(CLAVE_PLATAFORMA))))
                .andExpect(content().string(not(containsString("value=\"" + nueva + "\""))));
        assertThat(encoder.matches(CLAVE, usuarios.findById(administrador.getId()).orElseThrow().getPassword())).isTrue();
    }

    // Los usuarios de empresas no pueden acceder a estas funciones de plataforma.
    @Test
    void exigeRolDePlataforma() throws Exception {
        mvc.perform(get(base())).andExpect(redirectedUrl("/plataforma/login"));
        var cliente = new UsuarioPrincipal(administrador);
        mvc.perform(get(base()).with(user(cliente))).andExpect(status().isForbidden());
        mvc.perform(get(ruta(administrador.getId()) + "/editar").with(user(cliente))).andExpect(status().isForbidden());
        mvc.perform(edicion(administrador.getId(), "Prohibido", "prohibido").with(user(cliente)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post(ruta(administrador.getId()) + "/estado").with(user(cliente)).with(csrf()).param("activo", "false"))
                .andExpect(status().isForbidden());
        mvc.perform(clave(administrador.getId(), NUEVA, NUEVA, CLAVE_PLATAFORMA).with(user(cliente)).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // Las tres acciones de modificación requieren el token CSRF.
    @Test
    void exigeCsrfEnTodasLasModificaciones() throws Exception {
        mvc.perform(edicion(administrador.getId(), "Sin token", "sin.token").with(user(actor)))
                .andExpect(status().isForbidden());
        mvc.perform(post(ruta(administrador.getId()) + "/estado").with(user(actor)).param("activo", "false"))
                .andExpect(status().isForbidden());
        mvc.perform(clave(administrador.getId(), NUEVA, NUEVA, CLAVE_PLATAFORMA).with(user(actor)))
                .andExpect(status().isForbidden());
    }

    // Una cuenta de plataforma desactivada no puede modificar administradores.
    @Test
    void rechazaUnaCuentaDePlataformaDesactivada() throws Exception {
        plataforma.findById(actor.getId()).orElseThrow().setEstado(false);
        mvc.perform(edicion(administrador.getId(), "Prohibido", "prohibido").with(user(actor)).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().hasErrors());
        mvc.perform(post(ruta(administrador.getId()) + "/estado").with(user(actor)).with(csrf()).param("activo", "false"))
                .andExpect(flash().attributeExists("error"));
        mvc.perform(clave(administrador.getId(), NUEVA, NUEVA, CLAVE_PLATAFORMA).with(user(actor)).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().hasErrors());
        assertThat(usuarios.findById(administrador.getId()).orElseThrow().getUsuario()).isEqualTo("empresa.a.admin");
        assertThat(usuarios.findById(administrador.getId()).orElseThrow().getEstado()).isTrue();
    }

    // Activar una cuenta no reactiva una empresa suspendida.
    @Test
    void conservaLaSuspensionDeLaEmpresa() throws Exception {
        servicio.cambiarEstado(empresaA.getId(), administrador.getId(), actor.getId(), false);
        empresasService.cambiarEstado(empresaA.getId(), false);
        servicio.cambiarEstado(empresaA.getId(), administrador.getId(), actor.getId(), true);
        mvc.perform(get(base()).with(user(actor))).andExpect(status().isOk())
                .andExpect(content().string(containsString("La empresa está suspendida.")));
        mvc.perform(post("/login").with(csrf()).param("usuario", administrador.getUsuario()).param("password", CLAVE))
                .andExpect(status().isUnauthorized());
        assertThat(empresas.findById(empresaA.getId()).orElseThrow().getEstado()).isFalse();
    }
}
