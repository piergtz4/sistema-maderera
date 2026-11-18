package sistema_maderera;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.*;
import sistema_maderera.entity.*;
import sistema_maderera.exception.RecursoNoEncontradoException;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.*;
import sistema_maderera.service.*;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CatalogoEmpresaIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired EmpresaRepository empresas;
    @Autowired SedeRepository sedes;
    @Autowired UsuarioRepository usuarios;
    @Autowired ProductoRepository productos;
    @Autowired InventarioRepository inventarios;
    @Autowired CompraRepository compraRepository;
    @Autowired CategoriaService categorias;
    @Autowired ProductoService catalogo;
    @Autowired RegistroProductoService registro;
    @Autowired InventarioService existencias;
    @Autowired CompraService compras;
    @Autowired ProveedorService proveedores;
    @Autowired DashboardService dashboard;
    @Autowired PasswordEncoder encoder;

    private Empresa empresaA;
    private Empresa empresaB;
    private Categoria categoriaA;
    private Categoria categoriaB;
    private Producto productoA;
    private Producto productoB;
    private Sede sedeA;
    private Sede sedeB;
    private Usuario administradorA;

    @BeforeEach
    void preparar() {
        empresaA = empresa("Empresa de catálogo A");
        empresaB = empresa("Empresa de catálogo B");
        categoriaA = categoria(empresaA, "Catálogo A");
        categoriaB = categoria(empresaB, "Catálogo B");
        productoA = catalogo.guardar(empresaA.getId(), producto("Producto A", categoriaA));
        productoB = catalogo.guardar(empresaB.getId(), producto("Producto B", categoriaB));
        sedeA = sede(empresaA, "Local A");
        sedeB = sede(empresaB, "Local B");
        administradorA = new Usuario();
        administradorA.setEmpresa(empresaA);
        administradorA.setNombre("Administrador del catálogo A");
        administradorA.setUsuario("catalogo.a");
        administradorA.setPassword(encoder.encode("ClaveDePrueba1"));
        administradorA.setRol("ADMINISTRADOR_GENERAL");
        administradorA = usuarios.save(administradorA);
        existencias.registrar(sedeA.getId(), inventario(productoA));
        existencias.registrar(sedeB.getId(), inventario(productoB));
    }

    private Empresa empresa(String nombre) {
        Empresa empresa = new Empresa();
        empresa.setNombreComercial(nombre);
        return empresas.save(empresa);
    }

    private Categoria categoria(Empresa empresa, String nombre) {
        CategoriaForm form = new CategoriaForm();
        form.setNombre(nombre);
        return categorias.guardar(empresa.getId(), form);
    }

    private ProductoForm producto(String nombre, Categoria categoria) {
        ProductoForm form = new ProductoForm();
        form.setNombre(nombre);
        form.setCategoriaId(categoria.getId());
        form.setUnidadMedida("UND");
        form.setPrecioCompra(BigDecimal.ONE);
        form.setPrecioVenta(BigDecimal.TEN);
        return form;
    }

    private Sede sede(Empresa empresa, String nombre) {
        Sede sede = new Sede();
        sede.setEmpresa(empresa);
        sede.setCodigoDocumentos(1);
        sede.setNombre(nombre);
        sede.setDepartamento("Ica");
        sede.setProvincia("Ica");
        sede.setDistrito("Ica");
        return sedes.save(sede);
    }

    private RegistroInventarioForm inventario(Producto producto) {
        RegistroInventarioForm form = new RegistroInventarioForm();
        form.setProductoId(producto.getId());
        form.setStockMinimo(BigDecimal.ZERO);
        return form;
    }

    private MockHttpSession login() throws Exception {
        MockHttpSession sesion = (MockHttpSession) mvc.perform(post("/login").with(csrf())
                .param("usuario", "catalogo.a").param("password", "ClaveDePrueba1"))
                .andExpect(status().isOk()).andReturn().getRequest().getSession(false);
        mvc.perform(post("/sede-trabajo").session(sesion).with(csrf())
                .param("sedeId", sedeA.getId().toString())).andExpect(redirectedUrl("/dashboard"));
        return sesion;
    }

    // Cada empresa ve únicamente su catálogo y sus sedes.
    @Test
    void listaSoloCatalogoPropio() throws Exception {
        MockHttpSession sesion = login();
        mvc.perform(get("/categorias").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("CATÁLOGO A")))
                .andExpect(content().string(not(containsString("CATÁLOGO B"))));
        mvc.perform(get("/productos").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Producto A")))
                .andExpect(content().string(not(containsString("Producto B"))))
                .andExpect(content().string(not(containsString("Local B"))));
        mvc.perform(get("/productos/nuevo").session(sesion)).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("CATÁLOGO B"))));
    }

    // Cambiar el ID no permite editar ni desactivar registros ajenos.
    @Test
    void rechazaEdicionAjena() throws Exception {
        MockHttpSession sesion = login();
        mvc.perform(get("/categorias/editar/{id}", categoriaB.getId()).session(sesion))
                .andExpect(status().isNotFound());
        mvc.perform(get("/productos/editar/{id}", productoB.getId()).session(sesion))
                .andExpect(status().isNotFound());
        mvc.perform(post("/categorias/cambiar-estado/{id}", categoriaB.getId()).session(sesion).with(csrf()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/productos/cambiar-estado/{id}", productoB.getId()).session(sesion).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(categoriaB.getEstado()).isTrue();
        assertThat(productoB.getEstado()).isTrue();
    }

    // La empresa se obtiene de la sesión, aunque envíen otra desde el formulario.
    @Test
    void ignoraEmpresaEnviadaEnFormulario() throws Exception {
        mvc.perform(post("/categorias/guardar").session(login()).with(csrf())
                .param("nombre", "Categoría nueva").param("empresaId", empresaB.getId().toString()))
                .andExpect(redirectedUrl("/categorias"));
        assertThat(categorias.listarTodas(empresaA.getId())).extracting(Categoria::getNombre)
                .contains("CATEGORÍA NUEVA");
        assertThat(categorias.listarTodas(empresaB.getId())).extracting(Categoria::getNombre)
                .doesNotContain("CATEGORÍA NUEVA");
    }

    // Un producto no puede utilizar la categoría de otra empresa.
    @Test
    void rechazaCategoriaAjena() throws Exception {
        long antes = productos.count();
        mvc.perform(post("/productos/guardar").session(login()).with(csrf())
                .param("nombre", "Intento cruzado").param("categoriaId", categoriaB.getId().toString())
                .param("unidadMedida", "UND").param("precioCompra", "1").param("precioVenta", "10")
                .param("stockInicial", "0").param("stockMinimo", "0"))
                .andExpect(status().isNotFound());
        assertThat(productos.count()).isEqualTo(antes);
    }

    // Los datos del catálogo también se protegen cuando no cambia el stock.
    @Test
    void rechazaCambiosAProductoAjeno() {
        ProductoForm form = producto("Nombre alterado", categoriaA);
        form.setId(productoB.getId());
        assertThatThrownBy(() -> registro.actualizarEnSede(sedeA.getId(), administradorA.getId(), form))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(productos.findById(productoB.getId()).orElseThrow().getNombre()).isEqualTo("Producto B");

        ProductoForm nuevo = producto("Otro intento", categoriaB);
        nuevo.setStockInicial(BigDecimal.ZERO);
        nuevo.setStockMinimo(BigDecimal.ZERO);
        long antes = productos.count();
        assertThatThrownBy(() -> registro.crearEnSede(sedeB.getId(), administradorA.getId(), nuevo))
                .isInstanceOf(ReglaNegocioException.class);
        assertThat(productos.count()).isEqualTo(antes);
    }

    // Una sede no puede registrar productos de otra empresa.
    @Test
    void rechazaInventarioAjeno() {
        long antes = inventarios.count();
        assertThatThrownBy(() -> existencias.registrar(sedeA.getId(), inventario(productoB)))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThat(inventarios.count()).isEqualTo(antes);
        assertThat(existencias.sedesRegistradasPorProducto(empresaA.getId()))
                .containsOnlyKeys(productoA.getId());
    }

    // Compras ofrece y acepta únicamente productos de la empresa.
    @Test
    void comprasRespetanCatalogo() {
        assertThat(compras.productosDisponibles(empresaA.getId())).extracting(Producto::getId)
                .containsExactly(productoA.getId());
        ProveedorForm proveedor = new ProveedorForm();
        proveedor.setNombre("Proveedor del catálogo A");
        var creado = proveedores.guardar(empresaA.getId(), proveedor);
        CompraForm form = new CompraForm();
        form.setProveedorId(creado.getId());
        form.setReferencia("F001-CRUZADA");
        form.setFecha(compras.hoy());
        DetalleCompraForm linea = new DetalleCompraForm();
        linea.setProductoId(productoB.getId());
        linea.setCantidad(BigDecimal.ONE);
        linea.setPrecio(BigDecimal.ONE);
        form.getLineas().add(linea);
        long antes = compraRepository.count();
        assertThatThrownBy(() -> compras.guardar(sedeA.getId(), administradorA.getId(), form))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("catálogo");
        assertThat(compraRepository.count()).isEqualTo(antes);
    }

    // El indicador del dashboard excluye productos de otras empresas.
    @Test
    void dashboardCuentaSoloEmpresa() {
        var informe = dashboard.informe(sedeA.getId(), dashboard.periodo("hoy", "", ""));
        assertThat(informe.productosEmpresa()).isEqualTo(1);
    }

    // Dos empresas pueden tener categorías con el mismo nombre y código.
    @Test
    void permiteNombresEnDistintasEmpresas() {
        Categoria primera = categoria(empresaA, "Maderas");
        Categoria segunda = categoria(empresaB, "Maderas");
        assertThat(primera.getCodigo()).isEqualTo(segunda.getCodigo());
        assertThatThrownBy(() -> categoria(empresaA, "Maderas"))
                .isInstanceOf(ReglaNegocioException.class).hasMessageContaining("Ya existe");
    }
}
