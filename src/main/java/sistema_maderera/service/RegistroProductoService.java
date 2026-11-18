package sistema_maderera.service;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.MovimientoForm;
import sistema_maderera.dto.ProductoForm;
import sistema_maderera.dto.RegistroInventarioForm;
import sistema_maderera.dto.StockMinimoForm;
import sistema_maderera.entity.Inventario;
import sistema_maderera.entity.Producto;
import sistema_maderera.entity.Sede;
import sistema_maderera.entity.Usuario;
import sistema_maderera.entity.TipoMovimiento;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.exception.RecursoNoEncontradoException;
import sistema_maderera.repository.InventarioRepository;
import sistema_maderera.repository.SedeRepository;
import sistema_maderera.repository.UsuarioRepository;

/**
 * Registra productos y sus existencias en una sede.
 */
@Service
@Transactional
public class RegistroProductoService {
    private final ProductoService productos;
    private final InventarioService inventarios;
    private final InventarioRepository inventarioRepository;
    private final ValidacionService validacion;
    private final SedeRepository sedes;
    private final UsuarioRepository usuarios;

    public RegistroProductoService(ProductoService productos, InventarioService inventarios,
            InventarioRepository inventarioRepository, ValidacionService validacion,
            SedeRepository sedes, UsuarioRepository usuarios) {
        this.productos = productos;
        this.inventarios = inventarios;
        this.inventarioRepository = inventarioRepository;
        this.validacion = validacion;
        this.sedes = sedes;
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public ProductoForm formularioEnSede(Long sedeId, Long productoId) {
        Long empresaId = sedeActiva(sedeId).getEmpresa().getId();
        ProductoForm form = productos.formulario(empresaId, productoId);
        Inventario inventario = inventarioRepository.findByProductoIdAndSedeId(productoId, sedeId).orElse(null);
        form.setStockInicial(inventario == null ? BigDecimal.ZERO : inventario.getStock());
        form.setStockMinimo(inventario == null ? BigDecimal.ZERO : inventario.getStockMinimo());
        form.setInventarioVersion(inventario == null ? null : inventario.getVersion());
        return form;
    }

    /**
     * Registra un producto nuevo con su stock inicial y mínimo en la sede.
     *
     * @param sedeId identificador de la sede
     * @param usuarioId identificador del usuario responsable
     * @param form datos del producto y sus existencias
     * @return producto registrado
     * @throws ReglaNegocioException si los datos no permiten registrar el producto
     */
    public Producto crearEnSede(Long sedeId, Long usuarioId, ProductoForm form) {
        validacion.validar(form);
        if (form.getId() != null) throw new ReglaNegocioException("Este producto ya existe.");
        if (!Boolean.TRUE.equals(form.getEstado())) {
            throw new ReglaNegocioException("El producto nuevo debe estar activo para registrarlo en una sede.");
        }
        if (form.getStockInicial() == null || form.getStockMinimo() == null) {
            throw new ReglaNegocioException("Ingresa el stock actual y el stock mínimo de esta sede.");
        }
        Long empresaId = empresaAutorizada(sedeId, usuarioId);
        Producto producto = productos.guardar(empresaId, form);
        registrar(sedeId, usuarioId, producto.getId(), form.getStockInicial(), form.getStockMinimo());
        return producto;
    }

    public void actualizarEnSede(Long sedeId, Long usuarioId, ProductoForm form) {
        validacion.validar(form);
        if (form.getId() == null) throw new ReglaNegocioException("Selecciona un producto existente.");
        Long empresaId = empresaAutorizada(sedeId, usuarioId);
        productos.guardar(empresaId, form);
        if (form.getStockInicial() != null || form.getStockMinimo() != null) {
            actualizarInventario(empresaId, sedeId, usuarioId, form.getId(), form.getStockInicial(),
                    form.getStockMinimo(), form.getInventarioVersion());
        }
    }

    public void actualizarSoloInventario(Long sedeId, Long usuarioId, Long productoId, ProductoForm form) {
        Long empresaId = empresaAutorizada(sedeId, usuarioId);
        productos.buscar(empresaId, productoId);
        actualizarInventario(empresaId, sedeId, usuarioId, productoId, form.getStockInicial(),
                form.getStockMinimo(), form.getInventarioVersion());
    }

    private void actualizarInventario(Long empresaId, Long sedeId, Long usuarioId, Long productoId,
            BigDecimal stockActual, BigDecimal stockMinimo, Long version) {
        RegistroInventarioForm datos = new RegistroInventarioForm();
        datos.setProductoId(productoId);
        datos.setStockInicial(stockActual);
        datos.setStockMinimo(stockMinimo);
        validacion.validar(datos);
        Inventario registro = inventarioRepository.buscarProductoParaActualizar(productoId, sedeId).orElse(null);
        if (!Objects.equals(registro == null ? null : registro.getVersion(), version)) {
            throw new ReglaNegocioException("El inventario cambió mientras editabas. Vuelve a abrir el producto.");
        }
        if (registro == null) {
            if (!productos.buscar(empresaId, productoId).getEstado()
                    && stockActual.signum() == 0 && stockMinimo.signum() == 0) return;
            registrar(sedeId, usuarioId, productoId, stockActual, stockMinimo);
            return;
        }
        if (registro.getStockMinimo().compareTo(stockMinimo) != 0) {
            var minimo = new StockMinimoForm();
            minimo.setStockMinimo(stockMinimo);
            inventarios.cambiarMinimo(sedeId, registro.getId(), minimo);
        }
        if (registro.getStock().compareTo(stockActual) != 0) {
            MovimientoForm ajuste = new MovimientoForm();
            ajuste.setInventarioId(registro.getId());
            ajuste.setTipo(TipoMovimiento.AJUSTE);
            ajuste.setCantidad(stockActual);
            ajuste.setMotivo("Ajuste desde la edición del producto");
            inventarios.mover(sedeId, usuarioId, ajuste);
        }
    }

    private Sede sedeActiva(Long sedeId) {
        Sede sede = sedes.findById(sedeId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sede no encontrada."));
        if (!Boolean.TRUE.equals(sede.getEstado()) || !Boolean.TRUE.equals(sede.getEmpresa().getEstado())) {
            throw new ReglaNegocioException("La sede o la empresa está inactiva.");
        }
        return sede;
    }

    private Long empresaAutorizada(Long sedeId, Long usuarioId) {
        Sede sede = sedeActiva(sedeId);
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        boolean general = "ADMINISTRADOR_GENERAL".equals(usuario.getRol());
        boolean local = "ADMINISTRADOR".equals(usuario.getRol()) && usuario.getSede() != null
                && usuario.getSede().getId().equals(sedeId);
        if (!Boolean.TRUE.equals(usuario.getEstado())
                || !usuario.getEmpresa().getId().equals(sede.getEmpresa().getId()) || !(general || local)) {
            throw new ReglaNegocioException("El usuario no puede operar en esta sede.");
        }
        return sede.getEmpresa().getId();
    }

    private Inventario registrar(Long sedeId, Long usuarioId, Long productoId,
            BigDecimal stockInicial, BigDecimal stockMinimo) {
        RegistroInventarioForm registro = new RegistroInventarioForm();
        registro.setProductoId(productoId);
        registro.setStockMinimo(stockMinimo);
        Inventario inventario = inventarios.registrar(sedeId, registro);
        if (stockInicial.signum() > 0) {
            MovimientoForm entrada = new MovimientoForm();
            entrada.setInventarioId(inventario.getId());
            entrada.setTipo(TipoMovimiento.ENTRADA);
            entrada.setCantidad(stockInicial);
            entrada.setMotivo("Stock inicial al registrar el producto en la sede");
            inventarios.mover(sedeId, usuarioId, entrada);
        }
        return inventario;
    }
}
