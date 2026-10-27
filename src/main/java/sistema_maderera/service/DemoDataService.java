package sistema_maderera.service;

import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.*;
import sistema_maderera.entity.*;
import sistema_maderera.repository.*;

@Service
public class DemoDataService {
    private static final Logger log = LoggerFactory.getLogger(DemoDataService.class);
    private final SedeRepository sedes;
    private final EmpresaRepository empresas;
    private final UsuarioRepository usuarios;
    private final CategoriaRepository categorias;
    private final ProductoRepository productos;
    private final CategoriaService categoriaService;
    private final ProductoService productoService;
    private final InventarioService inventario;
    private final PasswordEncoder encoder;

    public DemoDataService(SedeRepository sedes, EmpresaRepository empresas,
            UsuarioRepository usuarios, CategoriaRepository categorias,
            ProductoRepository productos, CategoriaService categoriaService, ProductoService productoService,
            InventarioService inventario, PasswordEncoder encoder) {
        this.sedes = sedes;
        this.empresas = empresas;
        this.usuarios = usuarios;
        this.categorias = categorias;
        this.productos = productos;
        this.categoriaService = categoriaService;
        this.productoService = productoService;
        this.inventario = inventario;
        this.encoder = encoder;
    }

    @Transactional
    public void inicializar(String password) {
        if (sedes.count() > 0 || usuarios.count() > 0 || categorias.count() > 0 || productos.count() > 0) return;
        if (password == null || password.length() < 8) {
            throw new IllegalStateException("Configure MADERPRO_DEMO_PASSWORD con al menos 8 caracteres.");
        }
        Empresa empresa = empresas.findFirstByOrderByIdAsc().orElseGet(() -> {
            Empresa nueva = new Empresa();
            nueva.setNombreComercial("Mi Empresa Maderera");
            return empresas.save(nueva);
        });
        Sede parcona = sede(empresa, "Sede Parcona", "Parcona");
        Sede ica = sede(empresa, "Sede Ica", "Ica");
        Usuario admin = usuario(empresa, "admin", "Administrador general",
                "ADMINISTRADOR_GENERAL", null, password);
        Usuario adminIca = usuario(empresa, "admin.ica", "Administrador Ica",
                "ADMINISTRADOR", ica, password);
        usuario(empresa, "consulta", "Consulta Parcona", "CONSULTA", parcona, password);
        Categoria maderas = categoria("CAT-MAD", "Maderas");
        Categoria planchas = categoria("CAT-PLA", "Planchas");
        Producto melamina = producto("Plancha de melamina blanca", planchas.getId(), "UND", "120", "150");
        Producto tornillo = producto("Madera de tornillo", maderas.getId(), "P²", "8", "12");
        Producto triplay = producto("Plancha de triplay", planchas.getId(), "UND", "60", "85");
        existencia(parcona, admin, melamina, "20", "5");
        existencia(parcona, admin, tornillo, "3", "10");
        existencia(parcona, admin, triplay, "0", "4");
        existencia(ica, adminIca, melamina, "8", "2");
        existencia(ica, adminIca, tornillo, "35", "10");
        log.info("Demostración creada: dos sedes con inventarios independientes.");
    }

    private Sede sede(Empresa empresa, String nombre, String distrito) {
        Sede sede = new Sede();
        sede.setCodigoDocumentos(sedes.ultimoCodigoDocumentos(empresa.getId()) + 1);
        sede.setEmpresa(empresa);
        sede.setNombre(nombre);
        sede.setDepartamento("Ica");
        sede.setProvincia("Ica");
        sede.setDistrito(distrito);
        return sedes.save(sede);
    }

    private Usuario usuario(Empresa empresa, String login, String nombre, String rol,
            Sede sede, String password) {
        Usuario usuario = new Usuario();
        usuario.setEmpresa(empresa);
        usuario.setUsuario(login);
        usuario.setNombre(nombre);
        usuario.setRol(rol);
        usuario.setSede(sede);
        usuario.setPassword(encoder.encode(password));
        return usuarios.save(usuario);
    }

    private Categoria categoria(String codigo, String nombre) {
        CategoriaForm form = new CategoriaForm();
        form.setCodigo(codigo);
        form.setNombre(nombre);
        return categoriaService.guardar(form);
    }

    private Producto producto(String nombre, Long categoriaId, String unidad, String compra, String venta) {
        ProductoForm form = new ProductoForm();
        form.setNombre(nombre);
        form.setCategoriaId(categoriaId);
        form.setUnidadMedida(unidad);
        form.setPrecioCompra(new BigDecimal(compra));
        form.setPrecioVenta(new BigDecimal(venta));
        return productoService.guardar(form);
    }

    private void existencia(Sede sede, Usuario usuario, Producto producto, String stock, String minimo) {
        RegistroInventarioForm registro = new RegistroInventarioForm();
        registro.setProductoId(producto.getId());
        registro.setStockMinimo(new BigDecimal(minimo));
        Inventario item = inventario.registrar(sede.getId(), registro);
        if (new BigDecimal(stock).signum() == 0) return;
        MovimientoForm form = new MovimientoForm();
        form.setInventarioId(item.getId());
        form.setTipo(TipoMovimiento.ENTRADA);
        form.setCantidad(new BigDecimal(stock));
        form.setMotivo("Existencias iniciales de demostración");
        inventario.mover(sede.getId(), usuario.getId(), form);
    }
}
