package sistema_maderera.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.*;
import sistema_maderera.entity.*;
import sistema_maderera.exception.*;
import sistema_maderera.repository.*;

/**
 * Gestiona compras pendientes y el ingreso transaccional de mercadería en la sede receptora.
 */
@Service
@Transactional(readOnly = true)
public class CompraService {
    private final CompraRepository compras;
    private final ProveedorService proveedores;
    private final ProductoRepository productos;
    private final InventarioRepository inventarios;
    private final MovimientoInventarioRepository movimientos;
    private final UsuarioRepository usuarios;
    private final SedeRepository sedes;
    private final EmpresaRepository empresas;
    private final ValidacionService validacion;
    private final Clock clock;
    @PersistenceContext private EntityManager entityManager;

    public CompraService(CompraRepository compras, ProveedorService proveedores, ProductoRepository productos,
            InventarioRepository inventarios, MovimientoInventarioRepository movimientos, UsuarioRepository usuarios,
            SedeRepository sedes, EmpresaRepository empresas, ValidacionService validacion, Clock clock) {
        this.compras = compras;
        this.proveedores = proveedores;
        this.productos = productos;
        this.inventarios = inventarios;
        this.movimientos = movimientos;
        this.usuarios = usuarios;
        this.sedes = sedes;
        this.empresas = empresas;
        this.validacion = validacion;
        this.clock = clock;
    }

    public List<Compra> listar(Long sedeId) { return compras.findBySedeIdOrderByActualizadoDescIdDesc(sedeId); }
    public List<Producto> productosDisponibles() { return productos.findAllByOrderByNombreAsc(); }
    public LocalDate hoy() { return LocalDate.now(clock); }

    public Compra ver(Long sedeId, Long id) {
        Compra compra = compras.findByIdAndSedeId(id, sedeId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Compra no encontrada en esta sede."));
        compra.getLineas().size();
        return compra;
    }

    public CompraForm formulario(Long sedeId, Long id) {
        Compra compra = ver(sedeId, id);
        exigirPendiente(compra, compra.getVersion());
        CompraForm form = new CompraForm();
        form.setId(compra.getId());
        form.setVersion(compra.getVersion());
        form.setProveedorId(compra.getProveedor().getId());
        form.setTipo(compra.getTipo());
        form.setReferencia(compra.getReferencia());
        form.setFecha(compra.getFecha());
        form.setDetalles(compra.getDetalles());
        for (DetalleCompra linea : compra.getLineas()) {
            DetalleCompraForm item = new DetalleCompraForm();
            item.setProductoId(linea.getProducto().getId());
            item.setCantidad(linea.getCantidad().stripTrailingZeros());
            item.setPrecio(linea.getPrecio());
            form.getLineas().add(item);
        }
        return form;
    }

    /**
     * Guarda una compra pendiente y sus líneas. No ingresa existencias ni modifica los precios del catálogo.
     *
     * @param sedeId identificador de la sede de trabajo autorizada
     * @param usuarioId identificador del usuario que realiza la operación
     * @param form formulario con los datos que se validarán antes de guardar
     * @return compra pendiente
     * @throws sistema_maderera.exception.ReglaNegocioException si los datos, permisos o estado no permiten la operación
     */
    @Transactional
    public Compra guardar(Long sedeId, Long usuarioId, CompraForm form) {
        validacion.validar(form);
        Usuario usuario = usuario(usuarioId);
        Sede sede = autorizar(usuario, sedeId);
        Compra compra = form.getId() == null ? new Compra() : bloquear(sedeId, form.getId());
        exigirPendiente(compra, form.getVersion());
        if (form.getFecha().isAfter(hoy())) throw new ReglaNegocioException("La fecha de compra no puede ser futura.");
        Proveedor proveedor = proveedores.buscar(usuario.getEmpresa().getId(), form.getProveedorId());
        if (!proveedor.getEstado()) throw new ReglaNegocioException("Selecciona un proveedor activo.");
        String referencia = form.getReferencia().trim().replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        if (compras.findByProveedorIdAndTipoAndReferencia(proveedor.getId(), form.getTipo(), referencia)
                .filter(c -> !Objects.equals(c.getId(), compra.getId())).isPresent()) {
            throw new ReglaNegocioException("Ese comprobante del proveedor ya está registrado en una sede de la empresa.");
        }
        List<DetalleCompra> lineas = new ArrayList<>();
        Set<Long> usados = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (DetalleCompraForm item : form.getLineas()) {
            if (!usados.add(item.getProductoId())) throw new ReglaNegocioException("El producto está repetido; modifica su cantidad en la misma fila.");
            Producto producto = productos.findById(item.getProductoId())
                    .orElseThrow(() -> new ReglaNegocioException("Selecciona un producto del catálogo."));
            validarProducto(producto, item.getCantidad());
            DetalleCompra linea = new DetalleCompra();
            linea.setCompra(compra);
            linea.setProducto(producto);
            linea.setCodigo(producto.getCodigo());
            linea.setNombre(producto.getNombre());
            linea.setUnidadMedida(producto.getUnidadMedida());
            linea.setCantidad(item.getCantidad());
            linea.setPrecio(item.getPrecio().setScale(2));
            linea.setImporte(item.getCantidad().multiply(item.getPrecio()).setScale(2, RoundingMode.HALF_UP));
            total = total.add(linea.getImporte());
            lineas.add(linea);
        }
        if (total.compareTo(new BigDecimal("9999999999999999.99")) > 0) throw new ReglaNegocioException("El total supera el máximo permitido.");
        if (compra.getId() == null) { compra.setSede(sede); compra.setUsuario(usuario); }
        compra.setProveedor(proveedor);
        copiarProveedor(compra);
        compra.setTipo(form.getTipo());
        compra.setReferencia(referencia);
        compra.setFecha(form.getFecha());
        compra.setDetalles(form.getDetalles() == null ? "" : form.getDetalles().trim());
        compra.setTotal(total);
        compra.setActualizado(LocalDateTime.now(clock));
        compra.getLineas().clear();
        // Eliminar filas anteriores antes de insertar sus reemplazos con la misma clave de producto.
        if (compra.getId() != null) entityManager.flush();
        compra.getLineas().addAll(lineas);
        return compras.save(compra);
    }

    /**
     * Confirma una compra pendiente y aumenta el stock de la sede receptora.
     * Registra la entrada de los productos recibidos.
     *
     * @param sedeId identificador de la sede de trabajo autorizada
     * @param usuarioId identificador del usuario que realiza la operación
     * @param id identificador del documento de esta sede
     * @param version versión revisada, para detectar si el documento cambió
     * @return compra confirmada
     * @throws sistema_maderera.exception.ReglaNegocioException si los datos, permisos o estado no permiten la operación
     */
    @Transactional
    public Compra confirmar(Long sedeId, Long usuarioId, Long id, Long version) {
        Usuario usuario = usuario(usuarioId);
        Sede sede = autorizar(usuario, sedeId);
        Compra compra = bloquear(sedeId, id);
        exigirPendiente(compra, version);
        if (!compra.getProveedor().getEstado()) throw new ReglaNegocioException("El proveedor está inactivo.");
        if (compra.getLineas().isEmpty() || compra.getTotal().signum() <= 0) throw new ReglaNegocioException("Agrega productos con un total mayor que cero.");
        Map<Long, DetalleCompra> porInventario = new TreeMap<>();
        for (DetalleCompra linea : compra.getLineas()) {
            Producto producto = linea.getProducto();
            validarProducto(producto, linea.getCantidad());
            if (!producto.getUnidadMedida().equals(linea.getUnidadMedida())) throw new ReglaNegocioException("La unidad cambió. Vuelve a editar la compra.");
            Long inventarioId = inventarios.idPorProductoYSede(producto.getId(), sedeId).orElse(null);
            if (inventarioId == null) {
                Inventario nuevo = new Inventario();
                nuevo.setProducto(producto);
                nuevo.setSede(sede);
                inventarioId = inventarios.saveAndFlush(nuevo).getId();
            }
            porInventario.put(inventarioId, linea);
        }
        LocalDateTime ahora = LocalDateTime.now(clock);
        for (var entrada : porInventario.entrySet()) {
            Inventario inventario = inventarios.buscarParaActualizar(entrada.getKey(), sedeId).orElseThrow();
            BigDecimal anterior = inventario.getStock();
            BigDecimal posterior = anterior.add(entrada.getValue().getCantidad());
            if (posterior.compareTo(new BigDecimal("99999999.9999")) > 0) throw new ReglaNegocioException("El ingreso supera el stock máximo permitido para " + entrada.getValue().getNombre());
            inventario.setStock(posterior);
            MovimientoInventario movimiento = new MovimientoInventario();
            movimiento.setInventario(inventario);
            movimiento.setUsuario(usuario);
            movimiento.setTipo(TipoMovimiento.ENTRADA);
            movimiento.setCantidad(entrada.getValue().getCantidad());
            movimiento.setStockAnterior(anterior);
            movimiento.setStockPosterior(posterior);
            movimiento.setFecha(ahora);
            movimiento.setMotivo("Compra " + compra.getCodigo() + " · " + compra.getTipo() + " " + compra.getReferencia());
            movimientos.save(movimiento);
        }
        copiarProveedor(compra);
        compra.setEstado(EstadoCompra.CONFIRMADA);
        compra.setConfirmadoPor(usuario);
        compra.setConfirmadoEn(ahora);
        compra.setActualizado(ahora);
        return compras.save(compra);
    }

    @Transactional
    public void descartar(Long sedeId, Long usuarioId, Long id, Long version) {
        autorizar(usuario(usuarioId), sedeId);
        Compra compra = bloquear(sedeId, id);
        exigirPendiente(compra, version);
        compras.delete(compra);
    }

    private void copiarProveedor(Compra compra) {
        Proveedor p = compra.getProveedor();
        compra.setProveedorNombre(p.getNombre());
        compra.setProveedorDocumento(p.getNumeroDocumento() == null ? "Sin documento" : p.getTipoIdentificacion() + ": " + p.getNumeroDocumento());
    }

    private void validarProducto(Producto producto, BigDecimal cantidad) {
        if (!producto.getEstado() || !producto.getCategoria().getEstado()) throw new ReglaNegocioException("Producto o categoría inactivos: " + producto.getNombre());
        if ("UND".equals(producto.getUnidadMedida()) && cantidad.stripTrailingZeros().scale() > 0) throw new ReglaNegocioException("Los productos en unidades requieren cantidades enteras.");
    }

    private Usuario usuario(Long id) {
        return usuarios.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
    }

    private Compra bloquear(Long sedeId, Long id) {
        return compras.bloquear(id, sedeId).orElseThrow(() -> new RecursoNoEncontradoException("Compra no encontrada en esta sede."));
    }

    private void exigirPendiente(Compra compra, Long version) {
        if (compra.getEstado() != EstadoCompra.PENDIENTE) throw new ReglaNegocioException("La compra ya está confirmada; no se puede editar, descartar ni confirmar otra vez.");
        if (!Objects.equals(compra.getVersion(), version)) throw new ReglaNegocioException("La compra cambió. Vuelve a abrirla antes de continuar.");
    }

    private Sede autorizar(Usuario usuario, Long sedeId) {
        empresas.bloquearParaUsuarios(usuario.getEmpresa().getId()).orElseThrow();
        entityManager.refresh(usuario);
        entityManager.refresh(usuario.getEmpresa());
        Sede sede = sedes.bloquearParaNumeracion(sedeId)
                .filter(s -> s.getEmpresa().getId().equals(usuario.getEmpresa().getId()) && s.getEstado())
                .orElseThrow(() -> new ReglaNegocioException("Selecciona una sede activa de tu empresa."));
        boolean general = "ADMINISTRADOR_GENERAL".equals(usuario.getRol());
        boolean local = "ADMINISTRADOR".equals(usuario.getRol()) && usuario.getSede() != null && usuario.getSede().getId().equals(sedeId);
        if (!usuario.getEstado() || !usuario.getEmpresa().getEstado() || !(general || local)) throw new ReglaNegocioException("No tienes permiso para registrar compras en esta sede.");
        return sede;
    }
}
