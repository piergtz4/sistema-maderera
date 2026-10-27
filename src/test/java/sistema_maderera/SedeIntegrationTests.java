package sistema_maderera;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SedeIntegrationTests {
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
    private Usuario admin;
    private MockHttpSession sesion;

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
        parcona = sede(empresa, "Sede Parcona", "Parcona");
        admin = usuario("admin", "ADMINISTRADOR_GENERAL", null);
        usuario("local", "ADMINISTRADOR", parcona);
        sesion = autenticar("admin");
    }

    private Sede sede(Empresa empresaSede, String nombre, String distrito) {
        Sede sede = new Sede();
        sede.setCodigoDocumentos(Math.toIntExact(sedes.count()) + 1);
        sede.setEmpresa(empresaSede);
        sede.setNombre(nombre);
        sede.setDepartamento("Ica");
        sede.setProvincia("Ica");
        sede.setDistrito(distrito);
        return sedes.save(sede);
    }

    private Usuario usuario(String login, String rol, Sede sede) {
        Usuario usuario = new Usuario();
        usuario.setEmpresa(empresa);
        usuario.setSede(sede);
        usuario.setNombre(login);
        usuario.setUsuario(login);
        usuario.setRol(rol);
        usuario.setPassword(encoder.encode("ClaveDePrueba1"));
        return usuarios.save(usuario);
    }

    private MockHttpSession autenticar(String login) throws Exception {
        return (MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("usuario", login).param("password", "ClaveDePrueba1"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
    }

    @Test
    void administradorGeneralCreaYEditaSede() throws Exception {
        mvc.perform(get("/sedes").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sede Parcona")))
                .andExpect(content().string(containsString("Nueva sede")));
        mvc.perform(post("/sedes/guardar").session(sesion).with(csrf())
                .param("nombre", "  Sede   Nazca  ").param("departamento", "Ica")
                .param("provincia", "Nazca").param("distrito", "Nazca")
                .param("telefono", "999 111 222"))
                .andExpect(redirectedUrl("/sedes"));
        Sede nazca = sedes.findByEmpresaIdAndNombreIgnoreCase(empresa.getId(), "Sede Nazca").orElseThrow();
        assertThat(nazca.getEstado()).isTrue();
        mvc.perform(get("/sedes/{id}/editar", nazca.getId()).session(sesion)).andExpect(status().isOk())
                .andExpect(model().attributeExists("sede"));
        mvc.perform(post("/sedes/guardar").session(sesion).with(csrf())
                .param("id", nazca.getId().toString()).param("nombre", "Sede Nazca Centro")
                .param("departamento", "Ica").param("provincia", "Nazca")
                .param("distrito", "Vista Alegre").param("direccion", "Av. Principal 123"))
                .andExpect(redirectedUrl("/sedes"));
        Sede editada = sedes.findById(nazca.getId()).orElseThrow();
        assertThat(editada.getDistrito()).isEqualTo("Vista Alegre");
        assertThat(editada.getDireccion()).isEqualTo("Av. Principal 123");
    }

    @Test
    void rechazaDatosInvalidosYNombresRepetidos() throws Exception {
        mvc.perform(post("/sedes/guardar").session(sesion).with(csrf())
                .param("nombre", "  ").param("departamento", "Ica")
                .param("provincia", "Ica").param("distrito", "Ica"))
                .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("sede", "nombre"));
        mvc.perform(post("/sedes/guardar").session(sesion).with(csrf())
                .param("nombre", " sede parcona ").param("departamento", "Ica")
                .param("provincia", "Ica").param("distrito", "Parcona"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ya existe una sede con ese nombre")));
        assertThat(sedes.count()).isEqualTo(1);
    }

    @Test
    void desactivarConservaInventarioEHistorialYBorraSeleccion() throws Exception {
        Sede ica = sede(empresa, "Sede Ica", "Ica");
        Categoria categoria = new Categoria();
        categoria.setCodigo("CAT-MAD");
        categoria.setNombre("Maderas");
        categoria = categorias.save(categoria);
        Producto producto = new Producto();
        producto.setCodigo("PRO-000001");
        producto.setNombre("Melamina");
        producto.setCategoria(categoria);
        producto.setUnidadMedida("UND");
        producto = productos.save(producto);
        Inventario registro = new Inventario();
        registro.setSede(ica);
        registro.setProducto(producto);
        registro.setStock(new BigDecimal("7"));
        registro = inventarios.save(registro);
        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.setInventario(registro);
        movimiento.setUsuario(admin);
        movimiento.setTipo(TipoMovimiento.ENTRADA);
        movimiento.setCantidad(new BigDecimal("7"));
        movimiento.setStockAnterior(BigDecimal.ZERO);
        movimiento.setStockPosterior(new BigDecimal("7"));
        movimiento.setMotivo("Registro inicial");
        movimiento.setFecha(LocalDateTime.now());
        movimientos.save(movimiento);
        mvc.perform(post("/sede-trabajo").session(sesion).with(csrf())
                .param("sedeId", ica.getId().toString())).andExpect(redirectedUrl("/dashboard"));
        mvc.perform(post("/sedes/{id}/estado", ica.getId()).session(sesion).with(csrf()))
                .andExpect(redirectedUrl("/sedes"));
        assertThat(sedes.findById(ica.getId()).orElseThrow().getEstado()).isFalse();
        assertThat(inventarios.findById(registro.getId()).orElseThrow().getStock()).isEqualByComparingTo("7");
        assertThat(movimientos.count()).isEqualTo(1);
        mvc.perform(get("/dashboard").session(sesion)).andExpect(redirectedUrl("/seleccionar-sede"));
        assertThat(sesion.getAttribute("sedeTrabajoId")).isNull();
        mvc.perform(post("/sedes/{id}/estado", ica.getId()).session(sesion).with(csrf()))
                .andExpect(redirectedUrl("/sedes"));
        assertThat(sedes.findById(ica.getId()).orElseThrow().getEstado()).isTrue();
        mvc.perform(get("/dashboard").session(sesion)).andExpect(redirectedUrl("/seleccionar-sede"));
    }

    @Test
    void noPermiteDesactivarLaUltimaSedeActiva() throws Exception {
        mvc.perform(post("/sedes/{id}/estado", parcona.getId()).session(sesion).with(csrf()))
                .andExpect(redirectedUrl("/sedes"))
                .andExpect(flash().attribute("error", "No puedes desactivar la última sede activa de la empresa."));
        assertThat(sedes.findById(parcona.getId()).orElseThrow().getEstado()).isTrue();
    }

    @Test
    void cerrarSedeDesactivaSusUsuariosYReabrirlaNoLosReactiva() throws Exception {
        Sede ica = sede(empresa, "Sede Ica", "Ica");
        Usuario local = usuarios.findByUsuario("local").orElseThrow();
        Usuario otro = usuario("otro", "ADMINISTRADOR", ica);
        MockHttpSession sesionLocal = autenticar("local");

        mvc.perform(post("/sedes/{id}/estado", parcona.getId()).session(sesion).with(csrf()))
                .andExpect(redirectedUrl("/sedes"));
        assertThat(sedes.findById(parcona.getId()).orElseThrow().getEstado()).isFalse();
        assertThat(usuarios.findById(local.getId()).orElseThrow().getEstado()).isFalse();
        assertThat(usuarios.findById(otro.getId()).orElseThrow().getEstado()).isTrue();
        mvc.perform(get("/usuarios").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sede Parcona (inactiva)")));
        mvc.perform(get("/dashboard").session(sesionLocal)).andExpect(redirectedUrl("/?sesion"));
        mvc.perform(post("/login").with(csrf()).param("usuario", "local")
                .param("password", "ClaveDePrueba1")).andExpect(status().isUnauthorized());

        mvc.perform(post("/usuarios/{id}/estado", local.getId()).session(sesion).with(csrf()))
                .andExpect(flash().attribute("error", "Asigna una sede activa antes de activar esta cuenta."));
        assertThat(usuarios.findById(local.getId()).orElseThrow().getEstado()).isFalse();
        mvc.perform(post("/sedes/{id}/estado", parcona.getId()).session(sesion).with(csrf()))
                .andExpect(redirectedUrl("/sedes"));
        assertThat(usuarios.findById(local.getId()).orElseThrow().getEstado()).isFalse();
        mvc.perform(post("/login").with(csrf()).param("usuario", "local")
                .param("password", "ClaveDePrueba1")).andExpect(status().isUnauthorized());

        mvc.perform(post("/usuarios/{id}/estado", local.getId()).session(sesion).with(csrf()))
                .andExpect(redirectedUrl("/usuarios"));
        assertThat(usuarios.findById(local.getId()).orElseThrow().getEstado()).isTrue();
        MockHttpSession nuevaSesion = autenticar("local");
        mvc.perform(get("/dashboard").session(nuevaSesion)).andExpect(status().isOk())
                .andExpect(model().attribute("sedeNombre", "Sede Parcona"));
    }

    @Test
    void sedesAjenasYAdministradorLocalNoPuedenGestionarse() throws Exception {
        Empresa otra = new Empresa();
        otra.setNombreComercial("Otra empresa");
        otra = empresas.save(otra);
        Sede ajena = sede(otra, "Sede ajena", "Lima");
        mvc.perform(get("/sedes").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Sede ajena"))));
        mvc.perform(get("/sedes/{id}/editar", ajena.getId()).session(sesion))
                .andExpect(status().isNotFound());
        mvc.perform(post("/sedes/guardar").session(sesion).with(csrf())
                .param("id", ajena.getId().toString()).param("nombre", "Intrusa")
                .param("departamento", "Ica").param("provincia", "Ica").param("distrito", "Ica"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/sedes/{id}/estado", ajena.getId()).session(sesion).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(sedes.findById(ajena.getId()).orElseThrow().getEstado()).isTrue();
        MockHttpSession local = autenticar("local");
        mvc.perform(get("/sedes").session(local)).andExpect(status().isForbidden());
        mvc.perform(post("/sedes/guardar").session(local).with(csrf())
                .param("nombre", "Sede nueva")).andExpect(status().isForbidden());
    }
}
