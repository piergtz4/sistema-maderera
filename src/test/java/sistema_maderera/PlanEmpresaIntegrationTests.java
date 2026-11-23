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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.PlanEmpresaForm;
import sistema_maderera.entity.*;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.*;
import sistema_maderera.security.AdministradorPlataformaPrincipal;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.PlanEmpresaService;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlanEmpresaIntegrationTests {
    @Autowired PlanEmpresaService servicio;
    @Autowired EmpresaRepository empresas;
    @Autowired UsuarioRepository usuarios;
    @Autowired SedeRepository sedes;
    @Autowired AdministradorPlataformaRepository plataforma;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    private Empresa empresa;
    private AdministradorPlataformaPrincipal actor;

    @BeforeEach
    void preparar() {
        empresa = new Empresa();
        empresa.setNombreComercial("Empresa de planes");
        empresas.saveAndFlush(empresa);
        var proveedor = new AdministradorPlataforma();
        proveedor.setNombre("Responsable de planes");
        proveedor.setUsuario("planes.proveedor");
        proveedor.setPassword(encoder.encode("ClaveDePruebaPlanes123"));
        actor = new AdministradorPlataformaPrincipal(plataforma.saveAndFlush(proveedor));
    }

    private String ruta() { return "/plataforma/empresas/" + empresa.getId() + "/plan"; }

    private MockHttpServletRequestBuilder solicitud() {
        return post(ruta()).param("plan", "PROFESIONAL").param("periodicidad", "ANUAL")
                .param("sedesAdicionales", "1").param("usuariosAdicionales", "1").param("version", "0");
    }

    static Stream<Arguments> precios() {
        return Stream.of(
                Arguments.of(PlanComercial.BASICO, PeriodicidadPlan.MENSUAL, 0, 0, 1, 3, 500, "39.00", "39.00"),
                Arguments.of(PlanComercial.BASICO, PeriodicidadPlan.ANUAL, 0, 0, 1, 3, 500, "39.00", "468.00"),
                Arguments.of(PlanComercial.PROFESIONAL, PeriodicidadPlan.MENSUAL, 0, 0, 3, 7, 2000, "79.00", "79.00"),
                Arguments.of(PlanComercial.PROFESIONAL, PeriodicidadPlan.ANUAL, 0, 0, 3, 7, 2000, "79.00", "948.00"),
                Arguments.of(PlanComercial.BASICO, PeriodicidadPlan.MENSUAL, 1, 1, 2, 4, 500, "59.00", "59.00"),
                Arguments.of(PlanComercial.PROFESIONAL, PeriodicidadPlan.ANUAL, 1, 1, 4, 8, 2000, "99.00", "1188.00"));
    }

    // Comprueba los precios acordados y que los adicionales no aumentan el cupo de ventas.
    @ParameterizedTest
    @MethodSource("precios")
    void calculaImportesYCapacidades(PlanComercial plan, PeriodicidadPlan periodicidad,
            int extraSedes, int extraUsuarios, long totalSedes, long totalUsuarios, int ventas,
            String mensual, String periodo) {
        var form = new PlanEmpresaForm();
        form.setPlan(plan);
        form.setPeriodicidad(periodicidad);
        form.setSedesAdicionales(extraSedes);
        form.setUsuariosAdicionales(extraUsuarios);
        var resultado = servicio.calcular(form);
        assertThat(resultado.sedesTotales()).isEqualTo(totalSedes);
        assertThat(resultado.usuariosTotales()).isEqualTo(totalUsuarios);
        assertThat(resultado.plan().getVentasMensuales()).isEqualTo(ventas);
        assertThat(resultado.totalMensual()).isEqualByComparingTo(mensual);
        assertThat(resultado.totalPeriodo()).isEqualByComparingTo(periodo);
    }

    // Guarda solo la empresa elegida y conserva sus datos y su estado de acceso.
    @Test
    void guardaContratoSinAceptarPreciosNiEmpresaDesdeElFormulario() throws Exception {
        Empresa otra = new Empresa();
        otra.setNombreComercial("Empresa ajena");
        empresas.saveAndFlush(otra);
        empresa.setEstado(false);
        mvc.perform(solicitud().with(user(actor)).with(csrf())
                .param("empresaId", otra.getId().toString()).param("estado", "true")
                .param("totalMensual", "0.01").param("precio", "0"))
                .andExpect(redirectedUrl("/plataforma/empresas/" + empresa.getId() + "#plan"));
        em.flush();
        em.clear();
        var guardada = empresas.findById(empresa.getId()).orElseThrow();
        assertThat(guardada.getPlanComercial()).isEqualTo(PlanComercial.PROFESIONAL);
        assertThat(guardada.getPeriodicidadPlan()).isEqualTo(PeriodicidadPlan.ANUAL);
        assertThat(guardada.getEstado()).isFalse();
        assertThat(guardada.getVersionAcceso()).isZero();
        assertThat(guardada.getVersionPlan()).isEqualTo(1);
        assertThat(empresas.findById(otra.getId()).orElseThrow().getPlanComercial()).isNull();
        assertThat(servicio.resumen(empresa.getId()).totalPeriodo()).isEqualByComparingTo("1188.00");
        assertThat(servicio.formulario(empresa.getId()).getSedesAdicionales()).isEqualTo(1);
    }

    // Renderiza el formulario y el resumen guardado, con protección CSRF.
    @Test
    void muestraLaConfiguracionYLaNuevaPestana() throws Exception {
        mvc.perform(get(ruta()).with(user(actor))).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(containsString("data-precio=\"39.00\"")))
                .andExpect(content().string(containsString("sin descuento")));
        mvc.perform(get("/plataforma/empresas/{id}", empresa.getId()).with(user(actor)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Asignar plan")));
        servicio.guardar(empresa.getId(), actor.getId(), new PlanEmpresaForm());
        mvc.perform(get("/plataforma/empresas/{id}", empresa.getId()).with(user(actor)))
                .andExpect(status().isOk()).andExpect(content().string(containsString("panel-plan")))
                .andExpect(content().string(containsString("39.00")));
    }

    // Impide que una edición antigua reemplace un cambio más reciente.
    @Test
    void rechazaUnFormularioDesactualizado() throws Exception {
        servicio.guardar(empresa.getId(), actor.getId(), new PlanEmpresaForm());
        mvc.perform(solicitud().with(user(actor)).with(csrf())).andExpect(status().isOk())
                .andExpect(model().hasErrors()).andExpect(content().string(containsString("El plan cambió")));
        assertThat(servicio.resumen(empresa.getId()).plan()).isEqualTo(PlanComercial.BASICO);
    }

    // Rechaza valores negativos, incompletos y opciones inexistentes.
    @Test
    void validaLosCamposDelContrato() throws Exception {
        for (String[] campo : new String[][]{{"plan", "OTRO"}, {"periodicidad", "SEMANAL"},
                {"sedesAdicionales", "-1"}, {"usuariosAdicionales", ""}, {"version", "-1"}}) {
            mvc.perform(post(ruta()).with(user(actor)).with(csrf())
                    .param("plan", "BASICO").param("periodicidad", "MENSUAL")
                    .param("sedesAdicionales", "0").param("usuariosAdicionales", "0").param("version", "0")
                    .with(request -> { request.setParameter(campo[0], campo[1]); return request; }))
                    .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("contrato", campo[0]));
        }
        assertThat(servicio.resumen(empresa.getId())).isNull();
    }

    // No permite contratar menos capacidad que las sedes y cuentas que siguen activas.
    @Test
    void respetaLaCapacidadOcupadaYExcluyeInactivos() {
        for (int i = 0; i < 2; i++) {
            var sede = new Sede();
            sede.setEmpresa(empresa);
            sede.setNombre("Sede " + i);
            sede.setDepartamento("Lima"); sede.setProvincia("Lima"); sede.setDistrito("Ate");
            sede.setCodigoDocumentos(i + 1);
            sedes.saveAndFlush(sede);
        }
        var form = new PlanEmpresaForm();
        assertThatThrownBy(() -> servicio.guardar(empresa.getId(), actor.getId(), form))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("más sedes activas");
        form.setSedesAdicionales(1);
        for (int i = 0; i < 4; i++) {
            var usuario = new Usuario();
            usuario.setEmpresa(empresa); usuario.setNombre("Persona " + i); usuario.setUsuario("plan.persona" + i);
            usuario.setRol("ADMINISTRADOR_GENERAL"); usuario.setPassword("hashDePrueba");
            usuarios.saveAndFlush(usuario);
        }
        assertThatThrownBy(() -> servicio.guardar(empresa.getId(), actor.getId(), form))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("más cuentas activas");
        usuarios.findByUsuarioIgnoreCase("plan.persona3").orElseThrow().setEstado(false);
        servicio.guardar(empresa.getId(), actor.getId(), form);
        assertThat(servicio.resumen(empresa.getId()).totalMensual()).isEqualByComparingTo("54.00");
    }

    // Solo una cuenta de plataforma activa puede cambiar el contrato de una empresa.
    @Test
    void exigeSesionDePlataformaYTokenCsrf() throws Exception {
        var usuario = new Usuario();
        usuario.setEmpresa(empresa); usuario.setNombre("Dueño"); usuario.setUsuario("plan.dueno");
        usuario.setRol("ADMINISTRADOR_GENERAL"); usuario.setPassword(encoder.encode("ClaveDePruebaPlanes123"));
        var cliente = new UsuarioPrincipal(usuarios.saveAndFlush(usuario));
        mvc.perform(get(ruta()).with(user(cliente))).andExpect(status().isForbidden());
        mvc.perform(solicitud().with(user(cliente)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(solicitud().with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(get(ruta())).andExpect(redirectedUrl("/plataforma/login"));
        plataforma.findById(actor.getId()).orElseThrow().setEstado(false);
        assertThatThrownBy(() -> servicio.guardar(empresa.getId(), actor.getId(), new PlanEmpresaForm()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("no está activa");
        assertThat(servicio.resumen(empresa.getId())).isNull();
    }
}
