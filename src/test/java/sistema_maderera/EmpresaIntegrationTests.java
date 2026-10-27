package sistema_maderera;

import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import sistema_maderera.entity.*;
import sistema_maderera.repository.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmpresaIntegrationTests {
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
    private MockHttpSession sesionGeneral;

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
        empresa.setNombreComercial("Empresa inicial");
        empresa = empresas.save(empresa);
        parcona = new Sede();
        parcona.setCodigoDocumentos(1);
        parcona.setEmpresa(empresa);
        parcona.setNombre("Sede Parcona");
        parcona.setDepartamento("Ica");
        parcona.setProvincia("Ica");
        parcona.setDistrito("Parcona");
        parcona = sedes.save(parcona);
        usuario("admin", "Administrador general", "ADMINISTRADOR_GENERAL", null, empresa);
        usuario("operador", "Operador Parcona", "ADMINISTRADOR", parcona, empresa);
        sesionGeneral = autenticar("admin");
    }

    private Usuario usuario(String login, String nombre, String rol, Sede sede, Empresa empresaUsuario) {
        Usuario usuario = new Usuario();
        usuario.setEmpresa(empresaUsuario);
        usuario.setSede(sede);
        usuario.setUsuario(login);
        usuario.setNombre(nombre);
        usuario.setRol(rol);
        usuario.setPassword(encoder.encode("ClaveDePrueba1"));
        return usuarios.save(usuario);
    }

    private MockHttpSession autenticar(String usuario) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("usuario", usuario).param("password", "ClaveDePrueba1"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    private void seleccionar(Long sedeId) throws Exception {
        mvc.perform(post("/sede-trabajo").session(sesionGeneral).with(csrf())
                .param("sedeId", sedeId.toString())).andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    void administradorGeneralIniciaSinSedeYDebeSeleccionarUna() throws Exception {
        assertThat(usuarios.findByUsuario("admin").orElseThrow().getSede()).isNull();
        mvc.perform(get("/dashboard").session(sesionGeneral))
                .andExpect(redirectedUrl("/seleccionar-sede"));
        mvc.perform(get("/seleccionar-sede").session(sesionGeneral)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sede Parcona")));
        seleccionar(parcona.getId());
        mvc.perform(get("/dashboard").session(sesionGeneral)).andExpect(status().isOk())
                .andExpect(model().attribute("sedeNombre", "Sede Parcona"))
                .andExpect(content().string(containsString("Empresa inicial")));
    }

    @Test
    void seleccionDeSedeSeLimitaALaEmpresaAutenticada() throws Exception {
        Empresa otra = new Empresa();
        otra.setNombreComercial("Otra empresa");
        otra = empresas.save(otra);
        Sede ajena = new Sede();
        ajena.setCodigoDocumentos(1);
        ajena.setEmpresa(otra);
        ajena.setNombre("Sede ajena");
        ajena.setDepartamento("Lima");
        ajena.setProvincia("Lima");
        ajena.setDistrito("Lima");
        ajena = sedes.save(ajena);
        mvc.perform(post("/sede-trabajo").session(sesionGeneral).with(csrf())
                .param("sedeId", ajena.getId().toString())).andExpect(redirectedUrl("/seleccionar-sede"))
                .andExpect(flash().attribute("error", "Selecciona una sede activa de tu empresa."));
        sesionGeneral.setAttribute("sedeTrabajoId", ajena.getId());
        mvc.perform(get("/dashboard").session(sesionGeneral))
                .andExpect(redirectedUrl("/seleccionar-sede"));
    }

    @Test
    void administradorDeSedeNoPuedeCambiarSuContexto() throws Exception {
        MockHttpSession sesion = autenticar("operador");
        mvc.perform(get("/seleccionar-sede").session(sesion)).andExpect(status().isForbidden());
        mvc.perform(post("/sede-trabajo").session(sesion).with(csrf())
                .param("sedeId", parcona.getId().toString())).andExpect(status().isForbidden());
    }

    @Test
    void configuracionActualizaNombreYSeMuestraEnDashboard() throws Exception {
        seleccionar(parcona.getId());
        mvc.perform(post("/configuracion").session(sesionGeneral).with(csrf())
                .param("nombreComercial", "  Maderera   Los Andes  ")
                .param("razonSocial", "Maderera Los Andes S.A.C.")
                .param("ruc", "20123456789").param("telefono", "+51 999 111 222")
                .param("email", "ventas@losandes.test").param("direccion", "Ica"))
                .andExpect(redirectedUrl("/configuracion"));
        Empresa guardada = empresas.findById(empresa.getId()).orElseThrow();
        assertThat(guardada.getNombreComercial()).isEqualTo("Maderera Los Andes");
        assertThat(guardada.getRuc()).isEqualTo("20123456789");
        mvc.perform(get("/dashboard").session(sesionGeneral)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Maderera Los Andes")));
        mvc.perform(get("/productos").session(sesionGeneral)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Productos registrados en <span>Maderera Los Andes</span>")));
    }

    @Test
    void configuracionInvalidaNoModificaEmpresa() throws Exception {
        mvc.perform(post("/configuracion").session(sesionGeneral).with(csrf())
                .param("nombreComercial", "").param("ruc", "123"))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("empresa", "nombreComercial", "ruc"));
        assertThat(empresas.findById(empresa.getId()).orElseThrow().getNombreComercial())
                .isEqualTo("Empresa inicial");
    }

    @Test
    void logoValidoSeGuardaSirveYPuedeEliminarse() throws Exception {
        byte[] png = new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10, 1, 2, 3};
        MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", png);
        mvc.perform(multipart("/configuracion").file(logo).session(sesionGeneral).with(csrf())
                .param("nombreComercial", "Empresa inicial")).andExpect(redirectedUrl("/configuracion"));
        mvc.perform(get("/empresa/logo").session(sesionGeneral)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(content().bytes(png));
        mvc.perform(post("/configuracion").session(sesionGeneral).with(csrf())
                .param("nombreComercial", "Empresa inicial").param("eliminarLogo", "true"))
                .andExpect(redirectedUrl("/configuracion"));
        assertThat(empresas.findById(empresa.getId()).orElseThrow().getLogoDatos()).isNull();
        mvc.perform(get("/empresa/logo").session(sesionGeneral)).andExpect(status().isNotFound());
    }

    @Test
    void archivoQueNoEsImagenYLogoExcesivoSeRechazan() throws Exception {
        MockMultipartFile texto = new MockMultipartFile("logo", "falso.png", "image/png", "texto".getBytes());
        mvc.perform(multipart("/configuracion").file(texto).session(sesionGeneral).with(csrf())
                .param("nombreComercial", "Empresa inicial")).andExpect(status().isOk())
                .andExpect(content().string(containsString("PNG, JPG o WEBP válida")));
        byte[] grande = new byte[2 * 1024 * 1024 + 1];
        Arrays.fill(grande, (byte) 1);
        MockMultipartFile logoGrande = new MockMultipartFile("logo", "grande.png", "image/png", grande);
        mvc.perform(multipart("/configuracion").file(logoGrande).session(sesionGeneral).with(csrf())
                .param("nombreComercial", "Empresa inicial")).andExpect(status().isOk())
                .andExpect(content().string(containsString("no puede superar 2 MB")));
        assertThat(empresas.findById(empresa.getId()).orElseThrow().getLogoDatos()).isNull();
    }

    @Test
    void empresaInactivaImpideNuevosIniciosDeSesion() throws Exception {
        empresa.setEstado(false);
        empresas.save(empresa);
        mvc.perform(post("/login").with(csrf()).param("usuario", "admin")
                .param("password", "ClaveDePrueba1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/login").with(csrf()).param("usuario", "operador")
                .param("password", "ClaveDePrueba1")).andExpect(status().isUnauthorized());
    }

    @Test
    void soloAdministradorGeneralAccedeAConfiguracion() throws Exception {
        MockHttpSession operador = autenticar("operador");
        mvc.perform(get("/configuracion").session(operador)).andExpect(status().isForbidden());
        mvc.perform(post("/configuracion").session(operador).with(csrf())
                .param("nombreComercial", "Ataque")).andExpect(status().isForbidden());
        mvc.perform(get("/configuracion").session(sesionGeneral)).andExpect(status().isOk())
                .andExpect(model().attributeExists("empresa"));
    }
}
