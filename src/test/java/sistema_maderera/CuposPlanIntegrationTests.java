package sistema_maderera;

import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import sistema_maderera.dto.*;
import sistema_maderera.entity.*;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.*;
import sistema_maderera.security.AdministradorPlataformaPrincipal;
import sistema_maderera.security.UsuarioPrincipal;
import sistema_maderera.service.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CuposPlanIntegrationTests {
    @Autowired EmpresaRepository empresas;
    @Autowired SedeRepository sedes;
    @Autowired UsuarioRepository usuarios;
    @Autowired AdministradorPlataformaRepository plataforma;
    @Autowired SedeService gestionSedes;
    @Autowired UsuarioGestionService gestionUsuarios;
    @Autowired PlanEmpresaService planes;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    private Empresa empresa;
    private Sede principal;
    private Sede inactiva;
    private Usuario general;
    private Usuario encargado;
    private Usuario consulta;
    private Usuario usuarioInactivo;
    private UsuarioPrincipal actor;
    private String hash;
    private static final String CLAVE = "ClaveCuposPrueba123";

    @BeforeEach
    void preparar() {
        empresa = new Empresa();
        empresa.setNombreComercial("Empresa de cupos");
        empresa.setPlanComercial(PlanComercial.BASICO);
        empresas.saveAndFlush(empresa);
        principal = sede(empresa, "Principal", true);
        inactiva = sede(empresa, "Reservada", false);
        hash = encoder.encode(CLAVE);
        general = cuenta(empresa, "dueno", "ADMINISTRADOR_GENERAL", true);
        encargado = cuenta(empresa, "encargado", "ADMINISTRADOR", true);
        consulta = cuenta(empresa, "consulta", "CONSULTA", true);
        usuarioInactivo = cuenta(empresa, "inactivo", "ADMINISTRADOR", false);
        actor = new UsuarioPrincipal(general);
    }

    @AfterEach
    void limpiarPruebaConcurrente() {
        if (!TransactionSynchronizationManager.isActualTransactionActive() && empresa != null) {
            usuarios.deleteAll(usuarios.findByEmpresaIdOrderByNombreAsc(empresa.getId()));
            sedes.deleteAll(sedes.findByEmpresaIdOrderByEstadoDescNombreAsc(empresa.getId()));
            empresas.deleteById(empresa.getId());
        }
    }

    private Sede sede(Empresa propietaria, String nombre, boolean activa) {
        var sede = new Sede();
        sede.setEmpresa(propietaria); sede.setNombre(nombre); sede.setEstado(activa);
        sede.setDepartamento("Lima"); sede.setProvincia("Lima"); sede.setDistrito("Ate");
        sede.setCodigoDocumentos(sedes.ultimoCodigoDocumentos(propietaria.getId()) + 1);
        return sedes.saveAndFlush(sede);
    }

    private Usuario cuenta(Empresa propietaria, String nombre, String rol, boolean activo) {
        var usuario = new Usuario();
        usuario.setEmpresa(propietaria); usuario.setNombre(nombre); usuario.setRol(rol);
        usuario.setUsuario("cupos." + propietaria.getId() + "." + nombre);
        usuario.setPassword(hash); usuario.setEstado(activo);
        if (!rol.equals("ADMINISTRADOR_GENERAL")) usuario.setSede(principal);
        return usuarios.saveAndFlush(usuario);
    }

    private SedeForm nuevaSede(String nombre) {
        var form = new SedeForm();
        form.setNombre(nombre); form.setDepartamento("Lima"); form.setProvincia("Lima"); form.setDistrito("Ate");
        return form;
    }

    private UsuarioForm nuevaCuenta(String nombre) {
        var form = new UsuarioForm();
        form.setNombre(nombre); form.setUsuario("cupos." + empresa.getId() + "." + nombre);
        form.setRol("ADMINISTRADOR"); form.setSedeId(principal.getId());
        form.setPassword(CLAVE); form.setConfirmarPassword(CLAVE);
        return form;
    }

    // Rechaza crear y reactivar sedes cuando el plan ya está completo.
    @Test
    void bloqueaSedesDesdeElFormularioYDesdeElCambioDeEstado() throws Exception {
        mvc.perform(post("/sedes/guardar").with(user(actor)).with(csrf())
                .param("nombre", "Excedente").param("departamento", "Lima")
                .param("provincia", "Lima").param("distrito", "Ate"))
                .andExpect(status().isOk()).andExpect(model().hasErrors())
                .andExpect(content().string(containsString("ya alcanzaste ese límite")));
        mvc.perform(post("/sedes/{id}/estado", inactiva.getId()).with(user(actor)).with(csrf()))
                .andExpect(redirectedUrl("/sedes"))
                .andExpect(flash().attribute("error", containsString("ya alcanzaste ese límite")));
        assertThat(sedes.findByEmpresaIdAndNombreIgnoreCase(empresa.getId(), "Excedente")).isEmpty();
        assertThat(sedes.findById(inactiva.getId()).orElseThrow().getEstado()).isFalse();
        assertThat(sedes.countByEmpresaIdAndEstadoTrue(empresa.getId())).isEqualTo(1);
    }

    // Cuenta al administrador general y bloquea altas y reactivaciones de usuarios excedentes.
    @Test
    void bloqueaUsuariosSinCambiarSuEstadoNiSuVersionDeAcceso() throws Exception {
        mvc.perform(post("/usuarios/guardar").with(user(actor)).with(csrf())
                .param("nombre", "Excedente").param("usuario", "cupos.excedente")
                .param("rol", "ADMINISTRADOR").param("sedeId", principal.getId().toString())
                .param("password", CLAVE).param("confirmarPassword", CLAVE))
                .andExpect(status().isOk()).andExpect(model().hasErrors())
                .andExpect(content().string(containsString("ya alcanzaste ese límite")));
        mvc.perform(post("/usuarios/{id}/estado", usuarioInactivo.getId()).with(user(actor)).with(csrf()))
                .andExpect(redirectedUrl("/usuarios"))
                .andExpect(flash().attribute("error", containsString("ya alcanzaste ese límite")));
        assertThat(usuarios.findByUsuarioIgnoreCase("cupos.excedente")).isEmpty();
        assertThat(usuarioInactivo.getEstado()).isFalse();
        assertThat(usuarioInactivo.getVersionAcceso()).isZero();
        assertThat(usuarios.countByEmpresaIdAndEstadoTrue(empresa.getId())).isEqualTo(3);
    }

    // Editar un registro no consume otro lugar ni reactiva registros inactivos.
    @Test
    void permiteEditarAlLlegarAlLimite() {
        var sede = gestionSedes.formulario(empresa.getId(), principal.getId());
        sede.setNombre("Principal editada");
        gestionSedes.guardar(empresa.getId(), sede);
        var cuenta = gestionUsuarios.formulario(empresa.getId(), encargado.getId());
        cuenta.setNombre("Encargado editado");
        gestionUsuarios.guardar(empresa.getId(), general.getId(), cuenta);
        var desactivada = gestionUsuarios.formulario(empresa.getId(), usuarioInactivo.getId());
        desactivada.setNombre("Inactivo editado");
        gestionUsuarios.guardar(empresa.getId(), general.getId(), desactivada);
        var localInactivo = gestionSedes.formulario(empresa.getId(), inactiva.getId());
        localInactivo.setNombre("Reservada editada");
        gestionSedes.guardar(empresa.getId(), localInactivo);
        assertThat(principal.getNombre()).isEqualTo("Principal editada");
        assertThat(encargado.getNombre()).isEqualTo("Encargado editado");
        assertThat(usuarioInactivo.getEstado()).isFalse();
        assertThat(inactiva.getEstado()).isFalse();
    }

    // Los adicionales habilitan exactamente la cantidad contratada, incluyendo la modalidad anual.
    @Test
    void aplicaAdicionalesSinMultiplicarLosCuposPorDoce() {
        empresa.setSedesAdicionales(1); empresa.setUsuariosAdicionales(1);
        empresa.setPeriodicidadPlan(PeriodicidadPlan.ANUAL);
        empresas.saveAndFlush(empresa);
        gestionSedes.guardar(empresa.getId(), nuevaSede("Adicional"));
        gestionUsuarios.guardar(empresa.getId(), general.getId(), nuevaCuenta("adicional"));
        assertThatThrownBy(() -> gestionSedes.cambiarEstado(empresa.getId(), inactiva.getId()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("2 sedes activas");
        assertThatThrownBy(() -> gestionUsuarios.cambiarEstado(empresa.getId(), general.getId(), usuarioInactivo.getId()))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("4 cuentas activas");
    }

    // Verifica los límites propios del Básico y del Profesional.
    @ParameterizedTest
    @EnumSource(PlanComercial.class)
    void respetaLosCuposDeCadaPlan(PlanComercial plan) {
        empresa.setPlanComercial(plan); empresas.saveAndFlush(empresa);
        for (int i = 1; i < plan.getSedes(); i++) sede(empresa, "Activa " + i, true);
        for (int i = 3; i < plan.getUsuarios(); i++) cuenta(empresa, "activo" + i, "CONSULTA", true);
        assertThatThrownBy(() -> gestionSedes.guardar(empresa.getId(), nuevaSede("Excedente")))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya alcanzaste ese límite");
        assertThatThrownBy(() -> gestionUsuarios.guardar(empresa.getId(), general.getId(), nuevaCuenta("excedente")))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("ya alcanzaste ese límite");
    }

    // Desactivar una cuenta libera un lugar sin borrar su historial ni su registro.
    @Test
    void liberaCupoDeUsuariosAlDesactivar() {
        long registrados = usuarios.count();
        gestionUsuarios.cambiarEstado(empresa.getId(), general.getId(), encargado.getId());
        gestionUsuarios.cambiarEstado(empresa.getId(), general.getId(), usuarioInactivo.getId());
        assertThat(usuarios.count()).isEqualTo(registrados);
        assertThat(usuarios.countByEmpresaIdAndEstadoTrue(empresa.getId())).isEqualTo(3);
        assertThat(encargado.getEstado()).isFalse();
        assertThat(usuarioInactivo.getEstado()).isTrue();
    }

    // Al cerrar una sede se liberan sus cuentas; reabrirla no las activa automáticamente.
    @Test
    void liberaSedesYCuentasSinReactivacionesAutomaticas() {
        empresa.setSedesAdicionales(1); empresas.saveAndFlush(empresa);
        gestionSedes.cambiarEstado(empresa.getId(), inactiva.getId());
        gestionSedes.cambiarEstado(empresa.getId(), principal.getId());
        assertThat(encargado.getEstado()).isFalse();
        assertThat(consulta.getEstado()).isFalse();
        assertThat(usuarios.countByEmpresaIdAndEstadoTrue(empresa.getId())).isEqualTo(1);
        gestionSedes.cambiarEstado(empresa.getId(), principal.getId());
        assertThat(encargado.getEstado()).isFalse();
        assertThat(consulta.getEstado()).isFalse();
        assertThat(sedes.countByEmpresaIdAndEstadoTrue(empresa.getId())).isEqualTo(2);
    }

    // Cada empresa utiliza su propio cupo, independientemente de los registros de otras empresas.
    @Test
    void noMezclaEmpresasNiCuentaRegistrosInactivos() {
        var otra = new Empresa(); otra.setNombreComercial("Otra empresa"); empresas.saveAndFlush(otra);
        for (int i = 0; i < 5; i++) {
            sede(otra, "Ajena " + i, true);
            cuenta(otra, "ajeno" + i, "ADMINISTRADOR_GENERAL", true);
        }
        empresa.setSedesAdicionales(1); empresas.saveAndFlush(empresa);
        gestionUsuarios.cambiarEstado(empresa.getId(), general.getId(), encargado.getId());
        gestionSedes.cambiarEstado(empresa.getId(), inactiva.getId());
        gestionUsuarios.guardar(empresa.getId(), general.getId(), nuevaCuenta("nuevo"));
        assertThat(sedes.countByEmpresaIdAndEstadoTrue(empresa.getId())).isEqualTo(2);
        assertThat(usuarios.countByEmpresaIdAndEstadoTrue(empresa.getId())).isEqualTo(3);
        assertThat(sedes.countByEmpresaIdAndEstadoTrue(otra.getId())).isEqualTo(5);
    }

    // Mantiene la gestión anterior mientras la plataforma todavía no asigne un plan.
    @Test
    void conservaElFuncionamientoDeEmpresasSinPlan() {
        empresa.setPlanComercial(null); empresas.saveAndFlush(empresa);
        gestionSedes.cambiarEstado(empresa.getId(), inactiva.getId());
        gestionUsuarios.cambiarEstado(empresa.getId(), general.getId(), usuarioInactivo.getId());
        assertThat(planes.uso(empresa.getId()).sedesActivas()).isEqualTo(2);
        assertThat(planes.uso(empresa.getId()).usuariosActivos()).isEqualTo(4);
    }

    // La reactivación desde la plataforma también respeta el cupo de cuentas de la empresa.
    @Test
    void laPlataformaNoPuedeReactivarUnaCuentaFueraDelCupo() throws Exception {
        var anterior = cuenta(empresa, "general.anterior", "ADMINISTRADOR_GENERAL", false);
        var proveedor = new AdministradorPlataforma();
        proveedor.setNombre("Proveedor"); proveedor.setUsuario("cupos.proveedor"); proveedor.setPassword(hash);
        var principalPlataforma = new AdministradorPlataformaPrincipal(plataforma.saveAndFlush(proveedor));
        mvc.perform(post("/plataforma/empresas/{empresa}/administradores/{id}/estado", empresa.getId(), anterior.getId())
                .with(user(principalPlataforma)).with(csrf()).param("activo", "true"))
                .andExpect(flash().attribute("error", containsString("ya alcanzaste ese límite")));
        assertThat(anterior.getEstado()).isFalse();
    }

    // Cambiar de plan habilita la ampliación sin reiniciar ni alterar los registros existentes.
    @Test
    void cambiarDePlanActualizaLaCapacidadDisponible() {
        var proveedor = new AdministradorPlataforma();
        proveedor.setNombre("Proveedor"); proveedor.setUsuario("cupos.proveedor"); proveedor.setPassword(hash);
        plataforma.saveAndFlush(proveedor);
        var form = planes.formulario(empresa.getId()); form.setPlan(PlanComercial.PROFESIONAL);
        planes.guardar(empresa.getId(), proveedor.getId(), form);
        gestionSedes.cambiarEstado(empresa.getId(), inactiva.getId());
        gestionUsuarios.cambiarEstado(empresa.getId(), general.getId(), usuarioInactivo.getId());
        assertThat(sedes.countByEmpresaIdAndEstadoTrue(empresa.getId())).isEqualTo(2);
        assertThat(usuarios.countByEmpresaIdAndEstadoTrue(empresa.getId())).isEqualTo(4);
    }

    // Dos altas simultáneas no pueden ocupar el mismo último lugar disponible.
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void protegeElUltimoCupoAnteDosSolicitudes(boolean sonSedes) throws Exception {
        if (sonSedes) { empresa.setSedesAdicionales(1); empresas.saveAndFlush(empresa); }
        else gestionUsuarios.cambiarEstado(empresa.getId(), general.getId(), encargado.getId());
        var preparados = new CountDownLatch(2);
        var iniciar = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var tareas = new java.util.ArrayList<Future<Boolean>>();
            for (int i = 0; i < 2; i++) {
                String nombre = "simultaneo" + i;
                tareas.add(pool.submit(() -> {
                    preparados.countDown();
                    if (!iniciar.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("No se iniciaron las solicitudes.");
                    try {
                        if (sonSedes) gestionSedes.guardar(empresa.getId(), nuevaSede(nombre));
                        else gestionUsuarios.guardar(empresa.getId(), general.getId(), nuevaCuenta(nombre));
                        return true;
                    } catch (ReglaNegocioException e) {
                        assertThat(e).hasMessageContaining("ya alcanzaste ese límite");
                        return false;
                    }
                }));
            }
            try { assertThat(preparados.await(10, TimeUnit.SECONDS)).isTrue(); }
            finally { iniciar.countDown(); }
            int aceptadas = 0;
            for (var tarea : tareas) if (tarea.get(20, TimeUnit.SECONDS)) aceptadas++;
            assertThat(aceptadas).isEqualTo(1);
            assertThat(sonSedes ? sedes.countByEmpresaIdAndEstadoTrue(empresa.getId())
                    : usuarios.countByEmpresaIdAndEstadoTrue(empresa.getId())).isEqualTo(sonSedes ? 2 : 3);
        }
    }
}
