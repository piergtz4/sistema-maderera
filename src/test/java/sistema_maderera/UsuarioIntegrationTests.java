package sistema_maderera;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import sistema_maderera.entity.*;
import sistema_maderera.repository.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UsuarioIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired EmpresaRepository empresas;
    @Autowired SedeRepository sedes;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired ProductoRepository productos;
    @Autowired InventarioRepository inventarios;
    @Autowired MovimientoInventarioRepository movimientos;
    @Autowired TransferenciaRepository transferencias;
    @Autowired PasswordEncoder encoder;
    private Empresa empresa;
    private Sede parcona;
    private Sede ica;
    private Usuario admin;
    private Usuario local;
    private Usuario consulta;
    private MockHttpSession sesionAdmin;

    @BeforeEach
    void preparar() throws Exception {
        transferencias.deleteAll();
        movimientos.deleteAll();
        inventarios.deleteAll();
        productos.deleteAll();
        categorias.deleteAll();
        usuarios.deleteAll();
        sedes.deleteAll();
        empresas.deleteAll();
        empresa = new Empresa();
        empresa.setNombreComercial("Empresa de prueba");
        empresa = empresas.save(empresa);
        parcona = sede(empresa, "Sede Parcona");
        ica = sede(empresa, "Sede Ica");
        admin = usuario(empresa, "admin", "ADMINISTRADOR_GENERAL", null);
        local = usuario(empresa, "local", "ADMINISTRADOR", parcona);
        consulta = usuario(empresa, "consulta", "CONSULTA", parcona);
        sesionAdmin = autenticar("admin", "ClaveDePrueba1");
    }

    private Sede sede(Empresa empresaSede, String nombre) {
        Sede sede = new Sede();
        sede.setCodigoDocumentos(Math.toIntExact(sedes.count()) + 1);
        sede.setEmpresa(empresaSede);
        sede.setNombre(nombre);
        sede.setDepartamento("Ica");
        sede.setProvincia("Ica");
        sede.setDistrito(nombre);
        return sedes.save(sede);
    }

    private Usuario usuario(Empresa empresaUsuario, String login, String rol, Sede sede) {
        Usuario usuario = new Usuario();
        usuario.setEmpresa(empresaUsuario);
        usuario.setUsuario(login);
        usuario.setNombre(login);
        usuario.setRol(rol);
        usuario.setSede(sede);
        usuario.setPassword(encoder.encode("ClaveDePrueba1"));
        return usuarios.save(usuario);
    }

    private MockHttpSession autenticar(String login, String password) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("usuario", login).param("password", password))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    @Test
    void administradorCreaYEditaUsuarioSinExponerClave() throws Exception {
        mvc.perform(get("/usuarios").session(sesionAdmin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sede Parcona")));
        mvc.perform(get("/usuarios/nuevo").session(sesionAdmin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Nuevo usuario")));
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("nombre", "  María   Pérez  ").param("usuario", "Maria.Ica")
                .param("rol", "ADMINISTRADOR").param("sedeId", ica.getId().toString())
                .param("password", "ClaveSegura2").param("confirmarPassword", "ClaveSegura2"))
                .andExpect(redirectedUrl("/usuarios"));
        Usuario maria = usuarios.findByUsuarioIgnoreCase("maria.ica").orElseThrow();
        assertThat(maria.getNombre()).isEqualTo("María Pérez");
        assertThat(maria.getSede().getId()).isEqualTo(ica.getId());
        assertThat(encoder.matches("ClaveSegura2", maria.getPassword())).isTrue();
        String hash = maria.getPassword();
        mvc.perform(get("/usuarios/{id}/editar", maria.getId()).session(sesionAdmin))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString(hash))));
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("id", maria.getId().toString()).param("nombre", "María Pérez")
                .param("usuario", "maria.ica").param("rol", "CONSULTA")
                .param("sedeId", ica.getId().toString()))
                .andExpect(redirectedUrl("/usuarios"));
        assertThat(usuarios.findById(maria.getId()).orElseThrow().getPassword()).isEqualTo(hash);
        autenticar("MARIA.ICA", "ClaveSegura2");
    }

    @Test
    void rechazaDuplicadosRolesSedesYClavesInvalidas() throws Exception {
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("nombre", "Duplicado").param("usuario", "ADMIN")
                .param("rol", "ADMINISTRADOR_GENERAL").param("password", "ClaveSegura2")
                .param("confirmarPassword", "ClaveSegura2"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("ya está registrado")));
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("nombre", "Sin sede").param("usuario", "sin.sede")
                .param("rol", "CONSULTA").param("password", "ClaveSegura2")
                .param("confirmarPassword", "ClaveSegura2"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Selecciona una sede")));
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("nombre", "Sin clave").param("usuario", "sin.clave")
                .param("rol", "ADMINISTRADOR_GENERAL"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("al menos 12 caracteres")));
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("nombre", "Clave simple").param("usuario", "clave.simple")
                .param("rol", "ADMINISTRADOR_GENERAL").param("password", "123456789012")
                .param("confirmarPassword", "123456789012"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("letras y números")));
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("nombre", "Distintas").param("usuario", "distintas")
                .param("rol", "ADMINISTRADOR_GENERAL").param("password", "ClaveSegura2")
                .param("confirmarPassword", "OtraClave2"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("no coinciden")));
        assertThat(usuarios.count()).isEqualTo(3);
    }

    @Test
    void cambiarRolYSedeInvalidaSesionAnterior() throws Exception {
        MockHttpSession sesionLocal = autenticar("local", "ClaveDePrueba1");
        mvc.perform(get("/inventario").session(sesionLocal)).andExpect(status().isOk())
                .andExpect(model().attribute("sedeNombre", "Sede Parcona"));
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("id", local.getId().toString()).param("nombre", "local")
                .param("usuario", "local").param("rol", "CONSULTA")
                .param("sedeId", ica.getId().toString()))
                .andExpect(redirectedUrl("/usuarios"));
        mvc.perform(get("/inventario").session(sesionLocal)).andExpect(redirectedUrl("/?sesion"));
        assertThat(sesionLocal.isInvalid()).isTrue();
        MockHttpSession nueva = autenticar("local", "ClaveDePrueba1");
        mvc.perform(get("/movimientos").session(nueva)).andExpect(status().isOk())
                .andExpect(model().attribute("sedeNombre", "Sede Ica"))
                .andExpect(content().string(not(containsString("action=\"/movimientos\""))));
    }

    @Test
    void restablecerClaveInvalidaSesionYClaveAnterior() throws Exception {
        MockHttpSession sesionConsulta = autenticar("consulta", "ClaveDePrueba1");
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("id", consulta.getId().toString()).param("nombre", "consulta")
                .param("usuario", "consulta").param("rol", "CONSULTA")
                .param("sedeId", parcona.getId().toString())
                .param("password", "ClaveNueva2026").param("confirmarPassword", "ClaveNueva2026")
                .param("claveAdministrador", "ClaveDePrueba1"))
                .andExpect(redirectedUrl("/usuarios"));
        mvc.perform(get("/dashboard").session(sesionConsulta)).andExpect(redirectedUrl("/?sesion"));
        mvc.perform(post("/login").with(csrf()).param("usuario", "consulta")
                .param("password", "ClaveDePrueba1")).andExpect(status().isUnauthorized());
        autenticar("consulta", "ClaveNueva2026");
    }

    @Test
    void restablecerClaveAjenaRequiereClaveActualDelAdministrador() throws Exception {
        String hash = usuarios.findById(local.getId()).orElseThrow().getPassword();
        mvc.perform(get("/usuarios/{id}/editar", local.getId()).session(sesionAdmin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"claveAdministrador\"")))
                .andExpect(content().string(not(containsString(hash))));
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("id", local.getId().toString()).param("nombre", "local")
                .param("usuario", "local").param("rol", "ADMINISTRADOR")
                .param("sedeId", parcona.getId().toString())
                .param("password", "ClaveNueva2026").param("confirmarPassword", "ClaveNueva2026"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("contraseña de administrador no es correcta")));
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("id", local.getId().toString()).param("nombre", "local")
                .param("usuario", "local").param("rol", "ADMINISTRADOR")
                .param("sedeId", parcona.getId().toString())
                .param("password", "ClaveNueva2026").param("confirmarPassword", "ClaveNueva2026")
                .param("claveAdministrador", "incorrecta"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("contraseña de administrador no es correcta")));
        assertThat(usuarios.findById(local.getId()).orElseThrow().getPassword()).isEqualTo(hash);
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("id", local.getId().toString()).param("nombre", "local actualizado")
                .param("usuario", "local").param("rol", "ADMINISTRADOR")
                .param("sedeId", parcona.getId().toString())
                .param("claveAdministrador", "ClaveDePrueba1"))
                .andExpect(redirectedUrl("/usuarios"));
        assertThat(usuarios.findById(local.getId()).orElseThrow().getPassword()).isEqualTo(hash);
    }

    @Test
    void cadaUsuarioPuedeCambiarSuClaveActual() throws Exception {
        MockHttpSession sesionLocal = autenticar("local", "ClaveDePrueba1");
        mvc.perform(get("/mi-cuenta").session(sesionLocal)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Cambiar contraseña")));
        mvc.perform(post("/mi-cuenta/clave").session(sesionLocal).with(csrf())
                .param("actual", "incorrecta").param("nueva", "ClaveNueva2026")
                .param("confirmacion", "ClaveNueva2026"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("no es correcta")));
        assertThat(encoder.matches("ClaveDePrueba1", usuarios.findById(local.getId()).orElseThrow().getPassword())).isTrue();
        mvc.perform(post("/mi-cuenta/clave").session(sesionLocal).with(csrf())
                .param("actual", "ClaveDePrueba1").param("nueva", "ClaveNueva2026")
                .param("confirmacion", "ClaveNueva2026"))
                .andExpect(redirectedUrl("/?clave"));
        assertThat(sesionLocal.isInvalid()).isTrue();
        autenticar("local", "ClaveNueva2026");
    }

    @Test
    void editarCuentaPropiaMuestraTodasYNoPermiteCambiarClave() throws Exception {
        String hash = usuarios.findById(admin.getId()).orElseThrow().getPassword();
        mvc.perform(get("/usuarios/{id}/editar", admin.getId()).session(sesionAdmin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Todas")))
                .andExpect(content().string(containsString("disabled")))
                .andExpect(content().string(containsString("Mi cuenta")))
                .andExpect(content().string(not(containsString("name=\"password\""))))
                .andExpect(content().string(not(containsString("name=\"confirmarPassword\""))));
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("id", admin.getId().toString()).param("nombre", "Nombre alterado")
                .param("usuario", "admin").param("rol", "ADMINISTRADOR_GENERAL")
                .param("password", "ClaveNueva2026").param("confirmarPassword", "ClaveNueva2026"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ingresa la contraseña actual")));
        Usuario guardado = usuarios.findById(admin.getId()).orElseThrow();
        assertThat(guardado.getPassword()).isEqualTo(hash);
        assertThat(guardado.getNombre()).isEqualTo("admin");
    }

    @Test
    void desactivarCuentaInvalidaSesionYProtegeAlAdministradorGeneral() throws Exception {
        MockHttpSession sesionLocal = autenticar("local", "ClaveDePrueba1");
        mvc.perform(post("/usuarios/{id}/estado", admin.getId()).session(sesionAdmin).with(csrf()))
                .andExpect(flash().attribute("error", "No puedes desactivar tu propia cuenta."));
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("id", admin.getId().toString()).param("nombre", "admin")
                .param("usuario", "admin").param("rol", "CONSULTA")
                .param("sedeId", parcona.getId().toString()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("propio acceso")));
        mvc.perform(post("/usuarios/{id}/estado", local.getId()).session(sesionAdmin).with(csrf()))
                .andExpect(redirectedUrl("/usuarios"));
        mvc.perform(get("/inventario").session(sesionLocal)).andExpect(redirectedUrl("/?sesion"));
        mvc.perform(post("/login").with(csrf()).param("usuario", "local")
                .param("password", "ClaveDePrueba1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/usuarios/{id}/estado", local.getId()).session(sesionAdmin).with(csrf()))
                .andExpect(redirectedUrl("/usuarios"));
        autenticar("local", "ClaveDePrueba1");
    }

    @Test
    void cambioDeNombrePropioPideNuevoIngreso() throws Exception {
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("id", admin.getId().toString()).param("nombre", "Administrador actualizado")
                .param("usuario", "admin").param("rol", "ADMINISTRADOR_GENERAL"))
                .andExpect(redirectedUrl("/?cuenta"));
        assertThat(sesionAdmin.isInvalid()).isTrue();
        autenticar("admin", "ClaveDePrueba1");
    }

    @Test
    void guardarCuentaPropiaSinCambiosConservaSesion() throws Exception {
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("id", admin.getId().toString()).param("nombre", "admin")
                .param("usuario", "admin").param("rol", "ADMINISTRADOR_GENERAL"))
                .andExpect(redirectedUrl("/usuarios"));
        assertThat(sesionAdmin.isInvalid()).isFalse();
        mvc.perform(get("/usuarios").session(sesionAdmin)).andExpect(status().isOk());
    }

    @Test
    void segundoAdministradorGeneralPuedeReasignarAlPrimeroSinPerderAdministracion() throws Exception {
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("nombre", "Segundo administrador").param("usuario", "admin.dos")
                .param("rol", "ADMINISTRADOR_GENERAL").param("password", "ClaveSegura2")
                .param("confirmarPassword", "ClaveSegura2"))
                .andExpect(redirectedUrl("/usuarios"));
        MockHttpSession segundo = autenticar("admin.dos", "ClaveSegura2");
        mvc.perform(post("/usuarios/guardar").session(segundo).with(csrf())
                .param("id", admin.getId().toString()).param("nombre", "admin")
                .param("usuario", "admin").param("rol", "CONSULTA")
                .param("sedeId", parcona.getId().toString()))
                .andExpect(redirectedUrl("/usuarios"));
        mvc.perform(get("/usuarios").session(sesionAdmin)).andExpect(redirectedUrl("/?sesion"));
        mvc.perform(get("/usuarios").session(segundo)).andExpect(status().isOk());
        assertThat(usuarios.countByEmpresaIdAndRolAndEstadoTrue(empresa.getId(), "ADMINISTRADOR_GENERAL"))
                .isEqualTo(1);
    }

    @Test
    void administradorLocalNoGestionaCuentasNiSedesAjenas() throws Exception {
        Empresa otra = new Empresa();
        otra.setNombreComercial("Otra empresa");
        otra = empresas.save(otra);
        Sede ajena = sede(otra, "Sede ajena");
        Usuario ajeno = usuario(otra, "ajeno", "CONSULTA", ajena);
        mvc.perform(get("/usuarios/{id}/editar", ajeno.getId()).session(sesionAdmin))
                .andExpect(status().isNotFound());
        mvc.perform(post("/usuarios/{id}/estado", ajeno.getId()).session(sesionAdmin).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin).with(csrf())
                .param("nombre", "Intruso").param("usuario", "intruso")
                .param("rol", "CONSULTA").param("sedeId", ajena.getId().toString())
                .param("password", "ClaveSegura2").param("confirmarPassword", "ClaveSegura2"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("sede activa de tu empresa")));
        MockHttpSession sesionLocal = autenticar("local", "ClaveDePrueba1");
        mvc.perform(get("/usuarios").session(sesionLocal)).andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/guardar").session(sesionLocal).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/usuarios/guardar").session(sesionAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    void desactivarSedeInvalidaSesionLocalAbierta() throws Exception {
        MockHttpSession sesionLocal = autenticar("local", "ClaveDePrueba1");
        mvc.perform(post("/sedes/{id}/estado", parcona.getId()).session(sesionAdmin).with(csrf()))
                .andExpect(redirectedUrl("/sedes"));
        mvc.perform(get("/productos").session(sesionLocal)).andExpect(redirectedUrl("/?sesion"));
    }
}
