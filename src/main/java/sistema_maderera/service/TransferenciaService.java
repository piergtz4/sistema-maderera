package sistema_maderera.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.*;
import sistema_maderera.entity.*;
import sistema_maderera.exception.ReglaNegocioException;
import sistema_maderera.repository.*;

/**
 * Registra traslados entre sedes con una salida y una entrada vinculadas.
 */
@Service
@Transactional(readOnly = true)
public class TransferenciaService {
    private final TransferenciaRepository transferencias;
    private final InventarioRepository inventarios;
    private final SedeRepository sedes;
    private final UsuarioRepository usuarios;
    private final InventarioService inventarioService;
    private final ValidacionService validacion;
    private final Clock clock;

    public TransferenciaService(TransferenciaRepository transferencias, InventarioRepository inventarios,
            SedeRepository sedes, UsuarioRepository usuarios, InventarioService inventarioService,
            ValidacionService validacion, Clock clock) {
        this.transferencias = transferencias;
        this.inventarios = inventarios;
        this.sedes = sedes;
        this.usuarios = usuarios;
        this.inventarioService = inventarioService;
        this.validacion = validacion;
        this.clock = clock;
    }

    public List<TransferenciaVista> historial(Long sedeId) {
        return transferencias.findBySalidaInventarioSedeIdOrEntradaInventarioSedeIdOrderByIdDesc(sedeId, sedeId)
                .stream().map(t -> new TransferenciaVista(t.getId(),
                        t.getSalida().getInventario().getProducto().getNombre(),
                        t.getSalida().getInventario().getSede().getNombre(),
                        t.getEntrada().getInventario().getSede().getNombre(),
                        t.getCantidad(), t.getMotivo(), t.getSalida().getUsuario().getNombre(), t.getFecha()))
                .toList();
    }

    /**
     * Traslada productos entre sedes: descuenta del origen y suma al destino.
     * Solo el administrador general puede realizar esta operación.
     *
     * @param sedeOrigenId identificador de la sede que entrega las existencias
     * @param usuarioId identificador del usuario que realiza la operación
     * @param form producto de origen, sede de destino, cantidad y motivo
     * @return transferencia con sus movimientos de origen y destino
     * @throws sistema_maderera.exception.ReglaNegocioException si los datos, permisos o estado no permiten la operación
     */
    @Transactional
    public Transferencia transferir(Long sedeOrigenId, Long usuarioId, TransferenciaForm form) {
        validacion.validar(form);
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException("Usuario no encontrado."));
        if (!usuario.getEstado() || !usuario.getEmpresa().getEstado()
                || !"ADMINISTRADOR_GENERAL".equals(usuario.getRol())) {
            throw new ReglaNegocioException("Solo el administrador general puede transferir productos.");
        }
        Long empresaId = usuario.getEmpresa().getId();
        Sede origen = sedes.findByIdAndEmpresaIdAndEstadoTrue(sedeOrigenId, empresaId)
                .orElseThrow(() -> new ReglaNegocioException("La sede de origen no está activa."));
        Sede destino = sedes.findByIdAndEmpresaIdAndEstadoTrue(form.getSedeDestinoId(), empresaId)
                .orElseThrow(() -> new ReglaNegocioException("Selecciona una sede de destino activa de tu empresa."));
        if (origen.getId().equals(destino.getId())) {
            throw new ReglaNegocioException("La sede de destino debe ser diferente a la de origen.");
        }
        Inventario registroOrigen = inventarios.findById(form.getInventarioOrigenId())
                .filter(i -> i.getSede().getId().equals(origen.getId()))
                .orElseThrow(() -> new ReglaNegocioException("El producto no pertenece a la sede de origen."));
        if (!registroOrigen.getProducto().getEstado()) {
            throw new ReglaNegocioException("El producto está inactivo.");
        }
        if ("UND".equals(registroOrigen.getProducto().getUnidadMedida())
                && form.getCantidad().stripTrailingZeros().scale() > 0) {
            throw new ReglaNegocioException("Los productos en unidades requieren cantidades enteras.");
        }
        Inventario registroDestino = inventarios.findByProductoIdAndSedeId(
                registroOrigen.getProducto().getId(), destino.getId()).orElse(null);
        if (registroDestino == null) {
            RegistroInventarioForm alta = new RegistroInventarioForm();
            alta.setProductoId(registroOrigen.getProducto().getId());
            registroDestino = inventarioService.registrar(destino.getId(), alta);
        }
        // Tomar ambos bloqueos en el mismo orden evita esperas cruzadas entre sedes.
        Inventario primero = registroOrigen.getId() < registroDestino.getId() ? registroOrigen : registroDestino;
        Inventario segundo = primero == registroOrigen ? registroDestino : registroOrigen;
        inventarios.buscarParaActualizar(primero.getId(), primero.getSede().getId()).orElseThrow();
        inventarios.buscarParaActualizar(segundo.getId(), segundo.getSede().getId()).orElseThrow();

        String motivo = form.getMotivo().trim();
        MovimientoInventario salida = inventarioService.mover(origen.getId(), usuarioId,
                movimiento(registroOrigen.getId(), TipoMovimiento.SALIDA, form.getCantidad(), motivo));
        MovimientoInventario entrada = inventarioService.mover(destino.getId(), usuarioId,
                movimiento(registroDestino.getId(), TipoMovimiento.ENTRADA, form.getCantidad(), motivo));
        Transferencia transferencia = new Transferencia();
        transferencia.setSalida(salida);
        transferencia.setEntrada(entrada);
        transferencia.setCantidad(form.getCantidad());
        transferencia.setMotivo(motivo);
        transferencia.setFecha(LocalDateTime.now(clock));
        return transferencias.save(transferencia);
    }

    private MovimientoForm movimiento(Long inventarioId, TipoMovimiento tipo, BigDecimal cantidad, String motivo) {
        MovimientoForm form = new MovimientoForm();
        form.setInventarioId(inventarioId);
        form.setTipo(tipo);
        form.setCantidad(cantidad);
        form.setMotivo("Transferencia: " + motivo);
        return form;
    }
}
