package sistema_maderera;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.PlataformaEmpresaForm;
import sistema_maderera.entity.AdministradorPlataforma;
import sistema_maderera.entity.Empresa;
import sistema_maderera.entity.Usuario;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.AdministradorPlataformaRepository;
import sistema_maderera.repository.CategoriaRepository;
import sistema_maderera.repository.EmpresaRepository;
import sistema_maderera.repository.ProductoRepository;
import sistema_maderera.repository.SedeRepository;
import sistema_maderera.repository.UsuarioRepository;
import sistema_maderera.security.AdministradorPlataformaPrincipal;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.PlataformaEmpresaService;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlataformaEmpresaIntegrationTests {
    private static final String CLAVE = "ClaveEmpresaPrueba123";
    @Autowired PlataformaEmpresaService servicio;
    @Autowired EmpresaRepository empresas;
    @Autowired SedeRepository sedes;
    @Autowired UsuarioRepository usuarios;
    @Autowired AdministradorPlataformaRepository administradores;
    @Autowired CategoriaRepository categorias;
    @Autowired ProductoRepository productos;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager entityManager;
    @Autowired MockMvc mvc;

    private PlataformaEmpresaForm formulario() {
        var form = new PlataformaEmpresaForm();
        form.getEmpresa().setNombreComercial("  Maderera   Nueva  ");
        form.getEmpresa().setRazonSocial("Maderera Nueva SAC");
        form.getEmpresa().setRuc("20987654321");
        form.getEmpresa().setEmail("contacto@example.com");
        form.getSede().setNombre("Local principal");
        form.getSede().setDepartamento("Lima");
        form.getSede().setProvincia("Lima");
        form.getSede().setDistrito("Ate");
        form.setNombreAdministrador("  Ana   Pérez  ");
        form.setUsuario("Nueva.Admin");
        form.setPassword(CLAVE);
        form.setConfirmarPassword(CLAVE);
        return form;
    }

    private AdministradorPlataformaPrincipal plataforma() {
        var administrador = new AdministradorPlataforma();
        administrador.setNombre("Administrador de plataforma");
        administrador.setUsuario("proveedor.prueba");
        administrador.setPassword(encoder.encode(CLAVE));
        return new AdministradorPlataformaPrincipal(administradores.save(administrador));
    }

    private MockHttpServletRequestBuilder solicitud() {
        return solicitud("Nueva.Admin", "20987654321");
    }

    private MockHttpServletRequestBuilder solicitud(String usuario, String ruc) {
        return post("/plataforma/empresas")
                .param("empresa.nombreComercial", "Maderera Nueva")
                .param("empresa.ruc", ruc)
                .param("empresa.email", "contacto@example.com")
                .param("sede.nombre", "Local principal")
                .param("sede.departamento", "Lima")
                .param("sede.provincia", "Lima")
                .param("sede.distrito", "Ate")
                .param("nombreAdministrador", "Ana Pérez")
                .param("usuario", usuario)
                .param("password", CLAVE)
                .param("confirmarPassword", CLAVE);
    }

    // Guarda la empresa, su sede y su administrador con contraseña protegida.
    @Test
    void registraEmpresaCompleta() {
        long total = empresas.count();
        var empresa = servicio.crear(formulario());
        entityManager.flush();
        entityManager.clear();
        var guardada = empresas.findById(empresa.getId()).orElseThrow();
        assertThat(empresas.count()).isEqualTo(total + 1);
        assertThat(guardada.getNombreComercial()).isEqualTo("Maderera Nueva");
        assertThat(guardada.getEstado()).isTrue();
        assertThat(sedes.findByEmpresaIdOrderByEstadoDescNombreAsc(empresa.getId()))
                .singleElement().satisfies(sede -> {
                    assertThat(sede.getNombre()).isEqualTo("Local principal");
                    assertThat(sede.getCodigoDocumentos()).isEqualTo(1);
                    assertThat(sede.getEstado()).isTrue();
                });
        var administrador = usuarios.findByUsuarioIgnoreCase("nueva.admin").orElseThrow();
        assertThat(administrador.getEmpresa().getId()).isEqualTo(empresa.getId());
        assertThat(administrador.getNombre()).isEqualTo("Ana Pérez");
        assertThat(administrador.getUsuario()).isEqualTo("nueva.admin");
        assertThat(administrador.getRol()).isEqualTo("ADMINISTRADOR_GENERAL");
        assertThat(administrador.getSede()).isNull();
        assertThat(administrador.getEstado()).isTrue();
        assertThat(administrador.getPassword()).isNotEqualTo(CLAVE);
        assertThat(encoder.matches(CLAVE, administrador.getPassword())).isTrue();
        assertThat(categorias.findByEmpresaIdOrderByNombreAsc(empresa.getId())).isEmpty();
        assertThat(productos.findByEmpresaIdOrderByNombreAsc(empresa.getId())).isEmpty();
    }

    // La cuenta nueva ingresa directamente cuando tiene una sola sede activa.
    @Test
    void permiteIngresoDelAdministradorNuevo() throws Exception {
        var empresa = servicio.crear(formulario());
        MockHttpSession sesion = (MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("usuario", "nueva.admin").param("password", CLAVE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andReturn().getRequest().getSession(false);
        mvc.perform(get("/seleccionar-sede").session(sesion))
                .andExpect(redirectedUrl("/dashboard"));
        var sede = sedes.findByEmpresaIdAndCodigoDocumentos(empresa.getId(), 1).orElseThrow();
        mvc.perform(post("/sede-trabajo").session(sesion).with(csrf())
                .param("sedeId", sede.getId().toString())).andExpect(redirectedUrl("/dashboard"));
        mvc.perform(get("/dashboard").session(sesion)).andExpect(status().isOk())
                .andExpect(model().attribute("sedeNombre", "Local principal"));
        mvc.perform(get("/productos").session(sesion)).andExpect(status().isOk());
        mvc.perform(get("/plataforma/empresas/nueva").session(sesion)).andExpect(status().isForbidden());
    }

    // El administrador de plataforma accede al formulario con su sesión real.
    @Test
    void muestraFormularioAlProveedor() throws Exception {
        plataforma();
        MockHttpSession sesion = (MockHttpSession) mvc.perform(post("/plataforma/login").with(csrf())
                .param("usuario", "proveedor.prueba").param("password", CLAVE))
                .andExpect(redirectedUrl("/plataforma"))
                .andReturn().getRequest().getSession(false);
        mvc.perform(get("/plataforma/empresas/nueva").session(sesion))
                .andExpect(status().isOk()).andExpect(view().name("plataforma/empresa-form"))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("name=\"empresa.ruc\"")));
        mvc.perform(get("/plataforma").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("/plataforma/empresas/nueva")));
    }

    // Ignora identificadores, roles y estados manipulados desde el formulario.
    @Test
    void registraDesdePanelSinAceptarCamposPrivados() throws Exception {
        var principal = plataforma();
        mvc.perform(solicitud().with(user(principal)).with(csrf())
                .param("empresa.id", "999999").param("empresa.estado", "false")
                .param("sede.id", "999999").param("sede.codigoDocumentos", "777")
                .param("rol", "ADMINISTRADOR_PLATAFORMA").param("estado", "false"))
                .andExpect(redirectedUrl("/plataforma"))
                .andExpect(flash().attributeExists("mensaje"));
        var administrador = usuarios.findByUsuarioIgnoreCase("nueva.admin").orElseThrow();
        Long empresaId = administrador.getEmpresa().getId();
        assertThat(empresaId).isNotEqualTo(999999L);
        assertThat(administrador.getEmpresa().getEstado()).isTrue();
        assertThat(administrador.getRol()).isEqualTo("ADMINISTRADOR_GENERAL");
        assertThat(administrador.getEstado()).isTrue();
        assertThat(sedes.findByEmpresaIdAndCodigoDocumentos(empresaId, 1)).isPresent();
    }

    // Conserva los datos corregibles sin devolver las contraseñas en el HTML.
    @Test
    void muestraErroresSinExponerLaClave() throws Exception {
        long total = empresas.count();
        mvc.perform(solicitud("Nueva.Admin", "invalido").with(user(plataforma())).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("plataforma/empresa-form"))
                .andExpect(model().attributeHasFieldErrors("alta", "empresa.ruc"))
                .andExpect(content().string(containsString("Maderera Nueva")))
                .andExpect(content().string(not(containsString(CLAVE))));
        assertThat(empresas.count()).isEqualTo(total);
    }

    // Rechaza un RUC ya registrado y muestra el mensaje en el panel.
    @Test
    void rechazaRucRepetido() throws Exception {
        servicio.crear(formulario());
        mvc.perform(solicitud("otra.cuenta", "20987654321").with(user(plataforma())).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().hasErrors())
                .andExpect(content().string(containsString("Ese RUC ya pertenece")))
                .andExpect(content().string(not(containsString(CLAVE))));
        assertThat(usuarios.findByUsuarioIgnoreCase("otra.cuenta")).isEmpty();
    }

    // Si la contraseña no coincide, no queda ninguna parte del alta guardada.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void revierteElAltaSiFallaElAdministrador() {
        long totalEmpresas = empresas.count();
        long totalSedes = sedes.count();
        long totalUsuarios = usuarios.count();
        var form = formulario();
        form.setConfirmarPassword("OtraClavePrueba123");
        assertThatThrownBy(() -> servicio.crear(form))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no coinciden");
        assertThat(empresas.count()).isEqualTo(totalEmpresas);
        assertThat(sedes.count()).isEqualTo(totalSedes);
        assertThat(usuarios.count()).isEqualTo(totalUsuarios);
        assertThat(empresas.existsByRuc(form.getEmpresa().getRuc())).isFalse();
    }

    // Un usuario repetido tampoco deja una empresa ni una sede incompletas.
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void revierteElAltaSiElUsuarioYaExiste() {
        var existente = new Empresa();
        existente.setNombreComercial("Empresa existente para prueba");
        existente = empresas.saveAndFlush(existente);
        var cuenta = new Usuario();
        cuenta.setEmpresa(existente);
        cuenta.setNombre("Administrador existente");
        cuenta.setUsuario("nueva.admin");
        cuenta.setPassword(encoder.encode(CLAVE));
        cuenta.setRol("ADMINISTRADOR_GENERAL");
        cuenta = usuarios.saveAndFlush(cuenta);
        try {
            long totalEmpresas = empresas.count();
            long totalSedes = sedes.count();
            long totalUsuarios = usuarios.count();
            assertThatThrownBy(() -> servicio.crear(formulario()))
                    .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya está registrado");
            assertThat(empresas.count()).isEqualTo(totalEmpresas);
            assertThat(sedes.count()).isEqualTo(totalSedes);
            assertThat(usuarios.count()).isEqualTo(totalUsuarios);
        } finally {
            usuarios.deleteById(cuenta.getId());
            empresas.deleteById(existente.getId());
        }
    }

    // Impide registrar empresas sin sesión de plataforma o sin el token CSRF.
    @Test
    void exigePermisoDePlataformaYCsrf() throws Exception {
        long total = empresas.count();
        mvc.perform(get("/plataforma/empresas/nueva"))
                .andExpect(redirectedUrl("/plataforma/login"));
        mvc.perform(solicitud().with(csrf()))
                .andExpect(redirectedUrl("/plataforma/login"));
        var empresa = servicio.crear(formulario());
        var cliente = new UsuarioPrincipal(usuarios.findByUsuarioIgnoreCase("nueva.admin").orElseThrow());
        mvc.perform(solicitud().with(user(cliente)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(solicitud().with(user(plataforma()))).andExpect(status().isForbidden());
        assertThat(empresas.count()).isEqualTo(total + 1);
        assertThat(empresa.getId()).isNotNull();
    }

    // Exige RUC y valida los campos de la primera sede también desde el servicio.
    @Test
    void validaDatosObligatorios() {
        var form = formulario();
        form.getEmpresa().setRuc("");
        assertThatThrownBy(() -> servicio.crear(form))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("RUC");
        form.getEmpresa().setRuc("20987654321");
        form.getSede().setDistrito("");
        assertThatThrownBy(() -> servicio.crear(form))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Distrito");
    }
}
