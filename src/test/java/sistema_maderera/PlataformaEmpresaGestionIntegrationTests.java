package sistema_maderera;

import sistema_maderera.entity.PlanComercial;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import sistema_maderera.entity.AdministradorPlataforma;
import sistema_maderera.entity.Empresa;
import sistema_maderera.entity.Usuario;
import sistema_maderera.repository.AdministradorPlataformaRepository;
import sistema_maderera.repository.EmpresaRepository;
import sistema_maderera.repository.SedeRepository;
import sistema_maderera.repository.UsuarioRepository;
import sistema_maderera.security.AdministradorPlataformaPrincipal;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.PlataformaEmpresaService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlataformaEmpresaGestionIntegrationTests {
    private static final String CLAVE = "ClaveEmpresaPrueba123";
    @Autowired PlataformaEmpresaService servicio;
    @Autowired EmpresaRepository empresas;
    @Autowired SedeRepository sedes;
    @Autowired UsuarioRepository usuarios;
    @Autowired AdministradorPlataformaRepository administradores;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager entityManager;
    @Autowired MockMvc mvc;
    private Empresa empresa;
    private AdministradorPlataformaPrincipal proveedor;

    @BeforeEach
    void preparar() {
        empresa = crear("Maderera de gestión", "20987654321", "gestion.admin");
        var administrador = new AdministradorPlataforma();
        administrador.setNombre("Administrador de plataforma");
        administrador.setUsuario("gestion.proveedor");
        administrador.setPassword(encoder.encode(CLAVE));
        proveedor = new AdministradorPlataformaPrincipal(administradores.save(administrador));
    }

    private Empresa crear(String nombre, String ruc, String usuario) {
        var form = new PlataformaEmpresaForm();
        form.setPlan(PlanComercial.PROFESIONAL);
        form.getEmpresa().setNombreComercial(nombre);
        form.getEmpresa().setRuc(ruc);
        form.getSede().setNombre("Local principal");
        form.getSede().setDepartamento("Lima");
        form.getSede().setProvincia("Lima");
        form.getSede().setDistrito("Ate");
        form.setNombreAdministrador("Responsable de la empresa");
        form.setUsuario(usuario);
        form.setPassword(CLAVE);
        form.setConfirmarPassword(CLAVE);
        return servicio.crear(form);
    }

    private MockHttpServletRequestBuilder edicion(String nombre, String ruc) {
        return post("/plataforma/empresas/{id}", empresa.getId())
                .param("nombreComercial", nombre).param("ruc", ruc)
                .param("razonSocial", "Maderera Editada SAC")
                .param("email", "contacto@example.com")
                .param("telefono", "999888777").param("direccion", "Av. Principal 123");
    }

    private MockHttpSession login(String usuario) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("usuario", usuario).param("password", CLAVE))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andReturn().getRequest().getSession(false);
    }

    // Edita los datos sin alterar el logo, el estado ni los registros asociados.
    @Test
    void editaSoloLosDatosPermitidos() throws Exception {
        var otra = crear("Empresa ajena", "20111222333", "otra.admin");
        empresa.setLogoDatos(new byte[]{1, 2, 3});
        empresa.setLogoTipo("image/png");
        servicio.cambiarEstado(empresa.getId(), false);
        long totalSedes = sedes.count();
        long totalUsuarios = usuarios.count();
        entityManager.flush();
        mvc.perform(get("/plataforma/empresas/{id}/editar", empresa.getId()).with(user(proveedor)))
                .andExpect(status().isOk()).andExpect(view().name("plataforma/empresa-editar"))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(edicion("  Maderera   Editada  ", "20987654321").with(user(proveedor)).with(csrf())
                .param("id", otra.getId().toString()).param("estado", "true")
                .param("versionAcceso", "0").param("eliminarLogo", "true"))
                .andExpect(redirectedUrl("/plataforma?estado=todas"))
                .andExpect(flash().attributeExists("mensaje"));
        entityManager.flush();
        entityManager.clear();
        var guardada = empresas.findById(empresa.getId()).orElseThrow();
        assertThat(guardada.getNombreComercial()).isEqualTo("Maderera Editada");
        assertThat(guardada.getRazonSocial()).isEqualTo("Maderera Editada SAC");
        assertThat(guardada.getRuc()).isEqualTo("20987654321");
        assertThat(guardada.getEmail()).isEqualTo("contacto@example.com");
        assertThat(guardada.getTelefono()).isEqualTo("999888777");
        assertThat(guardada.getDireccion()).isEqualTo("Av. Principal 123");
        assertThat(guardada.getEstado()).isFalse();
        assertThat(guardada.getVersionAcceso()).isEqualTo(1L);
        assertThat(guardada.getLogoDatos()).containsExactly(1, 2, 3);
        assertThat(sedes.count()).isEqualTo(totalSedes);
        assertThat(usuarios.count()).isEqualTo(totalUsuarios);
        assertThat(empresas.findById(otra.getId()).orElseThrow().getNombreComercial()).isEqualTo("Empresa ajena");
    }

    // Permite conservar el RUC propio, pero rechaza el de otra empresa.
    @Test
    void rechazaRucAjenoSinGuardarCambios() throws Exception {
        crear("Otra empresa", "20111222333", "otra.admin");
        mvc.perform(edicion("Nombre que no debe guardarse", "20111222333").with(user(proveedor)).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("plataforma/empresa-editar"))
                .andExpect(model().hasErrors())
                .andExpect(content().string(containsString("Ese RUC ya pertenece")));
        assertThat(empresas.findById(empresa.getId()).orElseThrow().getNombreComercial())
                .isEqualTo("Maderera de gestión");
    }

    // Muestra errores claros y conserva los datos que el usuario puede corregir.
    @Test
    void validaLaEdicion() throws Exception {
        mvc.perform(edicion("Nombre corregible", "123").with(user(proveedor)).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("empresa", "ruc"))
                .andExpect(content().string(containsString("Nombre corregible")));
        mvc.perform(edicion("Nombre corregible", "").with(user(proveedor)).with(csrf()))
                .andExpect(status().isOk()).andExpect(model().hasErrors())
                .andExpect(content().string(containsString("RUC: Campo obligatorio")));
        assertThat(empresas.findById(empresa.getId()).orElseThrow().getRuc()).isEqualTo("20987654321");
    }

    // Filtra empresas activas, suspendidas o todas sin mezclar los resultados.
    @Test
    void filtraPorEstado() throws Exception {
        var suspendida = crear("Empresa suspendida de prueba", "20111222333", "otra.admin");
        servicio.cambiarEstado(suspendida.getId(), false);
        mvc.perform(get("/plataforma").with(user(proveedor)))
                .andExpect(status().isOk()).andExpect(model().attribute("estado", "activas"))
                .andExpect(content().string(not(containsString("Empresa suspendida de prueba"))))
                .andExpect(model().attribute("empresas", everyItem(hasProperty("estado", is(true)))))
                .andExpect(model().attribute("empresas", hasItem(hasProperty("id", is(empresa.getId())))));
        mvc.perform(get("/plataforma").param("estado", "suspendidas").with(user(proveedor)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Empresa suspendida de prueba")))
                .andExpect(model().attribute("empresas", everyItem(hasProperty("estado", is(false)))))
                .andExpect(model().attribute("empresas", hasItem(hasProperty("id", is(suspendida.getId())))))
                .andExpect(content().string(containsString(">Reactivar</button>")));
        mvc.perform(get("/plataforma").param("estado", "todas").with(user(proveedor)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("empresas", hasItem(hasProperty("id", is(empresa.getId())))))
                .andExpect(model().attribute("empresas", hasItem(hasProperty("id", is(suspendida.getId())))));
        mvc.perform(get("/plataforma").param("estado", "invalido").with(user(proveedor)))
                .andExpect(status().isBadRequest());
    }

    // Suspender bloquea nuevos ingresos e invalida las sesiones abiertas.
    @Test
    void suspendeElAccesoSinBorrarRegistros() throws Exception {
        crear("Otra empresa activa", "20111222333", "otra.admin");
        MockHttpSession sesionAjena = login("otra.admin");
        MockHttpSession sesion = login("gestion.admin");
        long totalEmpresas = empresas.count();
        long totalUsuarios = usuarios.count();
        long totalSedes = sedes.count();
        mvc.perform(post("/plataforma/empresas/{id}/estado", empresa.getId())
                .with(user(proveedor)).with(csrf()).param("activo", "false"))
                .andExpect(redirectedUrl("/plataforma?estado=suspendidas"));
        mvc.perform(get("/productos").session(sesion)).andExpect(redirectedUrl("/?sesion"));
        assertThat(sesion.isInvalid()).isTrue();
        mvc.perform(get("/productos").session(sesionAjena)).andExpect(status().isOk());
        mvc.perform(post("/login").with(csrf()).param("usuario", "gestion.admin").param("password", CLAVE))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        assertThat(empresas.count()).isEqualTo(totalEmpresas);
        assertThat(usuarios.count()).isEqualTo(totalUsuarios);
        assertThat(sedes.count()).isEqualTo(totalSedes);
        assertThat(usuarios.findByUsuarioIgnoreCase("gestion.admin").orElseThrow().getEstado()).isTrue();
    }

    // Reactivar exige un nuevo ingreso y nunca recupera sesiones anteriores.
    @Test
    void reactivaSinRecuperarSesionesAntiguas() throws Exception {
        MockHttpSession antigua = login("gestion.admin");
        servicio.cambiarEstado(empresa.getId(), false);
        mvc.perform(post("/plataforma/empresas/{id}/estado", empresa.getId())
                .with(user(proveedor)).with(csrf()).param("activo", "true"))
                .andExpect(redirectedUrl("/plataforma?estado=activas"));
        mvc.perform(get("/productos").session(antigua)).andExpect(redirectedUrl("/?sesion"));
        MockHttpSession nueva = login("gestion.admin");
        mvc.perform(get("/productos").session(nueva)).andExpect(status().isOk());
        servicio.cambiarEstado(empresa.getId(), true);
        mvc.perform(get("/productos").session(nueva)).andExpect(status().isOk());
        assertThat(empresas.findById(empresa.getId()).orElseThrow().getVersionAcceso()).isEqualTo(2L);
    }

    // Reactivar la empresa respeta las cuentas y sedes desactivadas por separado.
    @Test
    void conservaLasDesactivacionesIndividuales() throws Exception {
        var cuenta = new Usuario();
        cuenta.setEmpresa(empresa);
        cuenta.setNombre("Cuenta desactivada");
        cuenta.setUsuario("gestion.inactiva");
        cuenta.setRol("ADMINISTRADOR_GENERAL");
        cuenta.setPassword(encoder.encode(CLAVE));
        cuenta.setEstado(false);
        usuarios.save(cuenta);
        var sede = sedes.findByEmpresaIdAndCodigoDocumentos(empresa.getId(), 1).orElseThrow();
        sede.setEstado(false);
        servicio.cambiarEstado(empresa.getId(), false);
        servicio.cambiarEstado(empresa.getId(), true);
        entityManager.flush();
        entityManager.clear();
        assertThat(sedes.findById(sede.getId()).orElseThrow().getEstado()).isFalse();
        assertThat(usuarios.findById(cuenta.getId()).orElseThrow().getEstado()).isFalse();
        mvc.perform(post("/login").with(csrf()).param("usuario", "gestion.inactiva").param("password", CLAVE))
                .andExpect(status().isUnauthorized());
    }

    // La gestión de empresas requiere sesión de plataforma y protección CSRF.
    @Test
    void exigePermisosYCsrf() throws Exception {
        var cliente = new UsuarioPrincipal(usuarios.findByUsuarioIgnoreCase("gestion.admin").orElseThrow());
        mvc.perform(get("/plataforma/empresas/{id}/editar", empresa.getId()).with(user(cliente)))
                .andExpect(status().isForbidden());
        mvc.perform(edicion("Cambio prohibido", "20987654321").with(user(cliente)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/plataforma/empresas/{id}/estado", empresa.getId()).with(user(cliente))
                .with(csrf()).param("activo", "false")).andExpect(status().isForbidden());
        mvc.perform(edicion("Cambio sin token", "20987654321").with(user(proveedor)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/plataforma/empresas/{id}/estado", empresa.getId())
                .with(user(proveedor)).param("activo", "false")).andExpect(status().isForbidden());
        assertThat(empresas.findById(empresa.getId()).orElseThrow().getEstado()).isTrue();
    }

    // Rechaza empresas inexistentes, cambios por GET y estados incompletos.
    @Test
    void rechazaSolicitudesInvalidas() throws Exception {
        mvc.perform(get("/plataforma/empresas/{id}/editar", Long.MAX_VALUE).with(user(proveedor)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/plataforma/empresas/{id}/estado", Long.MAX_VALUE).with(user(proveedor))
                .with(csrf()).param("activo", "false")).andExpect(status().isNotFound());
        mvc.perform(get("/plataforma/empresas/{id}/estado", empresa.getId()).with(user(proveedor)))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(post("/plataforma/empresas/{id}/estado", empresa.getId()).with(user(proveedor)).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/plataforma/empresas/{id}/estado", empresa.getId()).with(user(proveedor))
                .with(csrf()).param("activo", "invalido")).andExpect(status().isBadRequest());
        assertThat(empresas.findById(empresa.getId()).orElseThrow().getEstado()).isTrue();
    }
}
