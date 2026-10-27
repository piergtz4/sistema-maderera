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
import sistema_maderera.entity.TipoMovimiento;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.InventarioRepository;

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

    public RegistroProductoService(ProductoService productos, InventarioService inventarios,
            InventarioRepository inventarioRepository, ValidacionService validacion) {
        this.productos = productos;
        this.inventarios = inventarios;
        this.inventarioRepository = inventarioRepository;
        this.validacion = validacion;
    }

    @Transactional(readOnly = true)
    public ProductoForm formularioEnSede(Long sedeId, Long productoId) {
        ProductoForm form = productos.formulario(productoId);
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
        Producto producto = productos.guardar(form);
        registrar(sedeId, usuarioId, producto.getId(), form.getStockInicial(), form.getStockMinimo());
        return producto;
    }

    public void actualizarEnSede(Long sedeId, Long usuarioId, ProductoForm form) {
        validacion.validar(form);
        if (form.getId() == null) throw new ReglaNegocioException("Selecciona un producto existente.");
        productos.guardar(form);
        if (form.getStockInicial() != null || form.getStockMinimo() != null) {
            actualizarInventario(sedeId, usuarioId, form.getId(), form.getStockInicial(),
                    form.getStockMinimo(), form.getInventarioVersion());
        }
    }

    public void actualizarSoloInventario(Long sedeId, Long usuarioId, Long productoId, ProductoForm form) {
        productos.buscar(productoId);
        actualizarInventario(sedeId, usuarioId, productoId, form.getStockInicial(),
                form.getStockMinimo(), form.getInventarioVersion());
    }

    private void actualizarInventario(Long sedeId, Long usuarioId, Long productoId,
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
            if (!productos.buscar(productoId).getEstado()
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
