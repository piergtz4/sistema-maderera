package sistema_maderera.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.*;
import sistema_maderera.entity.*;
import sistema_maderera.exception.RecursoNoEncontradoException;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.*;

/**
 * Consulta existencias por sede y registra entradas, salidas y ajustes.
 */
@Service
@Transactional(readOnly = true)
public class InventarioService {
    private static final BigDecimal MAXIMO = new BigDecimal("99999999.9999");
    private final InventarioRepository inventarios;
    private final MovimientoInventarioRepository movimientos;
    private final ProductoService productos;
    private final SedeRepository sedes;
    private final UsuarioRepository usuarios;
    private final ValidacionService validacion;
    private final Clock clock;

    public InventarioService(InventarioRepository inventarios, MovimientoInventarioRepository movimientos,
            ProductoService productos, SedeRepository sedes, UsuarioRepository usuarios,
            ValidacionService validacion, Clock clock) {
        this.inventarios = inventarios;
        this.movimientos = movimientos;
        this.productos = productos;
        this.sedes = sedes;
        this.usuarios = usuarios;
        this.validacion = validacion;
        this.clock = clock;
    }

    /**
     * Consulta los productos registrados en el inventario de una sede.
     *
     * @param sedeId identificador de la sede
     * @return lista de productos con sus existencias; vacía si no hay registros
     */
    public List<InventarioVista> listar(Long sedeId) {
        return inventarios.listarPorSede(sedeId).stream().map(this::vista).toList();
    }

    public Map<Long, String> sedesRegistradasPorProducto() {
        Map<Long, String> sedesPorProducto = new HashMap<>();
        for (Inventario inventario : inventarios.listarSedesRegistradas()) {
            Sede sede = inventario.getSede();
            String nombre = sede.getNombre() + (sede.getEstado() ? "" : " (inactiva)");
            sedesPorProducto.merge(inventario.getProducto().getId(), nombre,
                    (anteriores, nueva) -> anteriores + ", " + nueva);
        }
        return sedesPorProducto;
    }

    public List<MovimientoVista> recientes(Long sedeId) {
        return movimientos.findTop10ByInventarioSedeIdOrderByFechaDescIdDesc(sedeId).stream()
                .map(this::vistaMovimiento).toList();
    }

    public List<MovimientoVista> historial(Long sedeId) {
        return movimientos.findByInventarioSedeIdOrderByFechaDescIdDesc(sedeId).stream()
                .map(this::vistaMovimiento).toList();
    }

    @Transactional
    public Inventario registrar(Long sedeId, RegistroInventarioForm form) {
        validacion.validar(form);
        Producto producto = productos.buscar(form.getProductoId());
        if (!producto.getEstado()) {
            throw new ReglaNegocioException("El producto está inactivo.");
        }
        if (inventarios.existsByProductoIdAndSedeId(producto.getId(), sedeId)) {
            throw new ReglaNegocioException("El producto ya está registrado en esta sede.");
        }
        Sede sede = sedes.findById(sedeId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Sede no encontrada."));
        if (!sede.getEstado()) throw new ReglaNegocioException("La sede está inactiva.");
        Inventario inventario = new Inventario();
        inventario.setProducto(producto);
        inventario.setSede(sede);
        inventario.setStockMinimo(form.getStockMinimo());
        return inventarios.save(inventario);
    }

    @Transactional
    public void cambiarMinimo(Long sedeId, Long id, StockMinimoForm form) {
        validacion.validar(form);
        Inventario inventario = buscarParaActualizar(sedeId, id);
        inventario.setStockMinimo(form.getStockMinimo());
        inventarios.save(inventario);
    }

    /**
     * Actualiza un inventario y registra la diferencia entre el stock anterior y posterior. En AJUSTE la cantidad es el nuevo stock absoluto; en ENTRADA y SALIDA es la cantidad a sumar o restar. No permite stock negativo.
     *
     * @param sedeId identificador de la sede de trabajo autorizada
     * @param usuarioId identificador del usuario que realiza la operación
     * @param form formulario con los datos que se validarán antes de guardar
     * @return movimiento registrado con diferencia firmada
     * @throws sistema_maderera.exception.ReglaNegocioException si los datos, permisos o estado no permiten la operación
     */
    @Transactional
    public MovimientoInventario mover(Long sedeId, Long usuarioId, MovimientoForm form) {
        validacion.validar(form);
        Inventario inventario = buscarParaActualizar(sedeId, form.getInventarioId());
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        boolean general = "ADMINISTRADOR_GENERAL".equals(usuario.getRol());
        boolean mismaEmpresa = usuario.getEmpresa().getId().equals(inventario.getSede().getEmpresa().getId());
        boolean sedePermitida = general || usuario.getSede() != null
                && usuario.getSede().getId().equals(sedeId) && usuario.getSede().getEstado();
        if (!mismaEmpresa || !sedePermitida || !usuario.getEstado() || !usuario.getEmpresa().getEstado()) {
            throw new ReglaNegocioException("El usuario no puede operar en esta sede.");
        }
        if (!inventario.getProducto().getEstado()) {
            throw new ReglaNegocioException("El producto está inactivo.");
        }
        BigDecimal cantidad = form.getCantidad();
        if (form.getTipo() != TipoMovimiento.AJUSTE && cantidad.signum() <= 0) {
            throw new ReglaNegocioException("La entrada o salida debe ser mayor que cero.");
        }
        if ("UND".equals(inventario.getProducto().getUnidadMedida())
                && cantidad.stripTrailingZeros().scale() > 0) {
            throw new ReglaNegocioException("Los productos en unidades requieren cantidades enteras.");
        }
        BigDecimal anterior = inventario.getStock();
        BigDecimal posterior = switch (form.getTipo()) {
            case ENTRADA -> anterior.add(cantidad);
            case SALIDA -> anterior.subtract(cantidad);
            case AJUSTE -> cantidad;
        };
        if (posterior.signum() < 0) throw new ReglaNegocioException("La salida supera el stock disponible.");
        if (posterior.compareTo(MAXIMO) > 0) throw new ReglaNegocioException("El stock supera el límite permitido.");
        BigDecimal diferencia = posterior.subtract(anterior);
        if (diferencia.signum() == 0) throw new ReglaNegocioException("El ajuste no cambia el stock.");
        inventario.setStock(posterior);
        inventarios.save(inventario);
        MovimientoInventario movimiento = new MovimientoInventario();
        movimiento.setInventario(inventario);
        movimiento.setUsuario(usuario);
        movimiento.setTipo(form.getTipo());
        movimiento.setCantidad(diferencia);
        movimiento.setStockAnterior(anterior);
        movimiento.setStockPosterior(posterior);
        movimiento.setMotivo(form.getMotivo().trim());
        movimiento.setFecha(LocalDateTime.now(clock));
        return movimientos.save(movimiento);
    }

    public DashboardResumen resumen(Long sedeId) {
        var registros = inventarios.listarPorSede(sedeId);
        var vistas = registros.stream().map(this::vista).toList();
        var alertas = vistas.stream().filter(vista -> vista.isStockBajo()).toList();
        BigDecimal valor = registros.stream().map(i -> i.getStock().multiply(i.getProducto().getPrecioCompra()))
                .reduce(BigDecimal.ZERO, (total, importe) -> total.add(importe));
        return new DashboardResumen(vistas.size(), vistas.stream().filter(i -> i.stock().signum() == 0).count(),
                alertas.size(), valor, alertas, recientes(sedeId));
    }

    private Inventario buscarParaActualizar(Long sedeId, Long id) {
        return inventarios.buscarParaActualizar(id, sedeId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Inventario no encontrado en esta sede."));
    }

    private InventarioVista vista(Inventario i) {
        Producto p = i.getProducto();
        return new InventarioVista(i.getId(), p.getCodigo(), p.getNombre(), p.getCategoria().getNombre(),
                i.getSede().getNombre(), p.getUnidadMedida(), i.getStock(), i.getStockMinimo(), p.getEstado());
    }

    private MovimientoVista vistaMovimiento(MovimientoInventario m) {
        return new MovimientoVista(m.getId(), m.getInventario().getProducto().getNombre(),
                m.getTipo(), m.getCantidad(), m.getStockAnterior(), m.getStockPosterior(),
                m.getMotivo(), m.getUsuario().getNombre(), m.getFecha());
    }
}
