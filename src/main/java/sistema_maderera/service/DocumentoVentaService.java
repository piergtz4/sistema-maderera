package sistema_maderera.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import sistema_maderera.dto.DetalleVentaForm;
import sistema_maderera.dto.PagoVentaForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sistema_maderera.dto.DocumentoVentaForm;
import sistema_maderera.entity.*;
import sistema_maderera.exception.*;
import sistema_maderera.repository.*;

/**
 * Gestiona documentos de venta y cotizaciones, sus pagos y los efectos sobre el inventario.
 */
@Service
@Transactional(readOnly = true)
public class DocumentoVentaService {
    private final DocumentoVentaRepository documentos;
    private final SedeRepository sedes;
    private final UsuarioRepository usuarios;
    private final ClienteService clientes;
    private final ValidacionService validacion;
    private final Clock clock;
    private final InventarioRepository inventarios;
    private final NumeracionDocumentoService numeracion;
    private final MovimientoInventarioRepository movimientos;
    private final EmpresaRepository empresas;
    private final CuentaEmpresaService cuentas;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    public DocumentoVentaService(DocumentoVentaRepository documentos, SedeRepository sedes,
            UsuarioRepository usuarios, ClienteService clientes, ValidacionService validacion, Clock clock,
            InventarioRepository inventarios, NumeracionDocumentoService numeracion,
            MovimientoInventarioRepository movimientos, EmpresaRepository empresas, CuentaEmpresaService cuentas) {
        this.documentos = documentos;
        this.sedes = sedes;
        this.usuarios = usuarios;
        this.clientes = clientes;
        this.validacion = validacion;
        this.clock = clock;
        this.inventarios = inventarios;
        this.numeracion = numeracion;
        this.movimientos = movimientos;
        this.empresas = empresas;
        this.cuentas = cuentas;
    }

    public List<DocumentoVenta> listar(Long sedeId) { return documentos.findBySedeIdOrderByActualizadoDescIdDesc(sedeId); }

    public DocumentoVentaForm formulario(Long sedeId, Long id) {
        DocumentoVenta doc = buscar(sedeId, id);
        exigirBorrador(doc);
        DocumentoVentaForm form = new DocumentoVentaForm();
        form.setId(doc.getId());
        form.setVersion(doc.getVersion());
        form.setTipo(doc.getTipo());
        form.setClienteId(doc.getCliente() == null ? null : doc.getCliente().getId());
        form.setDetalles(doc.getDetalles());
        for (DetalleVenta linea : doc.getLineas()) {
            DetalleVentaForm item = new DetalleVentaForm();
            item.setProductoId(linea.getProducto().getId());
            item.setCantidad(linea.getCantidad().stripTrailingZeros());
            item.setPrecio(linea.getPrecio());
            item.setImporteCobrar(linea.getImporteCobrar().compareTo(linea.getImporteCalculado()) == 0
                    ? null : linea.getImporteCobrar());
            form.getLineas().add(item);
        }
        for (PagoVenta pago : doc.getPagos()) {
            PagoVentaForm item = new PagoVentaForm();
            item.setMedio(pago.getMedio());
            item.setRecibido(pago.getRecibido().signum() == 0 ? null : pago.getRecibido());
            item.setReferencia(pago.getReferencia());
            item.setBanco(pago.getBanco());
            item.setCuentaDestinoId(pago.getCuentaDestino() == null ? null : pago.getCuentaDestino().getId());
            form.getPagos().add(item);
        }
        return form;
    }

    /**
     * Guarda un pendiente sin exigir que el pago esté completo. No asigna correlativo ni modifica stock.
     *
     * @param sedeId identificador de la sede de trabajo autorizada
     * @param usuarioId identificador del usuario que realiza la operación
     * @param form formulario con los datos que se validarán antes de guardar
     * @return documento pendiente guardado
     * @throws sistema_maderera.exception.ReglaNegocioException si los datos, permisos o estado no permiten la operación
     */
    @Transactional
    public DocumentoVenta guardarBorrador(Long sedeId, Long usuarioId, DocumentoVentaForm form) {
        return guardarBorrador(sedeId, usuarioId, form, false);
    }

    /**
     * Guarda productos e importes de un documento pendiente. La revisión exige pagos válidos; la confirmación posterior vuelve a verificar existencias.
     *
     * @param sedeId identificador de la sede de trabajo autorizada
     * @param usuarioId identificador del usuario que realiza la operación
     * @param form formulario con los datos que se validarán antes de guardar
     * @param revisar true para exigir los requisitos de revisión, incluido el pago completo en ventas
     * @return documento pendiente guardado
     * @throws sistema_maderera.exception.ReglaNegocioException si los datos, permisos o estado no permiten la operación
     */
    @Transactional
    public DocumentoVenta guardarBorrador(Long sedeId, Long usuarioId, DocumentoVentaForm form, boolean revisar) {
        validacion.validar(form);
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        Sede sede = autorizarYBloquear(usuario, sedeId);
        DocumentoVenta doc = form.getId() == null ? new DocumentoVenta() : documentos.bloquear(form.getId(), sedeId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento no encontrado en esta sede."));
        exigirBorrador(doc);
        if (!Objects.equals(doc.getVersion(), form.getVersion())) {
            throw new ReglaNegocioException("El borrador cambió mientras editabas. Vuelve a abrirlo.");
        }
        Cliente cliente = form.getClienteId() == null ? null : clientes.buscar(usuario.getEmpresa().getId(), form.getClienteId());
        if (cliente != null && !cliente.getEstado()) throw new ReglaNegocioException("Selecciona un cliente activo.");
        if (form.getTipo() == TipoDocumentoVenta.FACTURA
                && (cliente == null || cliente.getTipoIdentificacion() != TipoIdentificacion.RUC)) {
            throw new ReglaNegocioException("Para preparar una factura selecciona un cliente con RUC.");
        }
        if (doc.getId() == null) {
            doc.setSede(sede);
            doc.setUsuario(usuario);
            doc.setFecha(LocalDateTime.now(clock));
        }
        if (doc.getDocumentoOrigenId() != null && form.getTipo() == TipoDocumentoVenta.COTIZACION) {
            throw new ReglaNegocioException("Una venta copiada o convertida no puede cambiarse a cotización.");
        }
        doc.setTipo(form.getTipo());
        doc.setCliente(cliente);
        doc.setDetalles(form.getDetalles() == null ? "" : form.getDetalles().trim());
        doc.setActualizado(LocalDateTime.now(clock));
        List<DetalleVenta> lineas = prepararLineas(sedeId, form);
        doc.getLineas().clear();
        BigDecimal total = BigDecimal.ZERO;
        for (DetalleVenta linea : lineas) {
            linea.setDocumento(doc);
            doc.getLineas().add(linea);
            total = total.add(linea.getImporteCobrar());
        }
        doc.setTotal(total);
        doc.setTasaIgv(new BigDecimal("18.00"));
        doc.setSubtotal(total.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP));
        doc.setIgv(total.subtract(doc.getSubtotal()));
        prepararPagos(doc, form.getPagos());
        if (revisar && doc.getTipo() != TipoDocumentoVenta.COTIZACION) exigirPagoCompleto(doc);
        return documentos.save(doc);
    }

    private Sede autorizarYBloquear(Usuario usuario, Long sedeId) {
        empresas.bloquearParaUsuarios(usuario.getEmpresa().getId()).orElseThrow();
        entityManager.refresh(usuario);
        entityManager.refresh(usuario.getEmpresa());
        Sede sede = sedes.bloquearParaNumeracion(sedeId)
                .filter(s -> s.getEmpresa().getId().equals(usuario.getEmpresa().getId()) && s.getEstado())
                .orElseThrow(() -> new ReglaNegocioException("Selecciona una sede activa de tu empresa."));
        boolean general = "ADMINISTRADOR_GENERAL".equals(usuario.getRol());
        boolean local = "ADMINISTRADOR".equals(usuario.getRol()) && usuario.getSede() != null
                && usuario.getSede().getId().equals(sedeId);
        if (!usuario.getEstado() || !usuario.getEmpresa().getEstado() || !(general || local)) {
            throw new ReglaNegocioException("No tienes permiso para registrar documentos en esta sede.");
        }
        return sede;
    }

    private void prepararPagos(DocumentoVenta doc, List<PagoVentaForm> formularios) {
        if (doc.getTipo() == TipoDocumentoVenta.COTIZACION && !formularios.isEmpty()) {
            throw new ReglaNegocioException("Las cotizaciones no registran pagos.");
        }
        List<PagoVenta> pagos = new ArrayList<>();
        var referencias = new HashSet<String>();
        BigDecimal digital = BigDecimal.ZERO;
        BigDecimal recibido = BigDecimal.ZERO;
        for (PagoVentaForm form : formularios) {
            PagoVenta pago = new PagoVenta();
            pago.setDocumento(doc);
            pago.setMedio(form.getMedio());
            pago.setRecibido(form.getRecibido() == null ? BigDecimal.ZERO : form.getRecibido().setScale(2));
            pago.setAplicado(BigDecimal.ZERO);
            if (form.getMedio() != MedioPago.EFECTIVO) {
                String referencia = form.getReferencia() == null ? "" : form.getReferencia().trim();
                CuentaEmpresa destino = form.getCuentaDestinoId() == null ? null : cuentas.buscar(doc.getSede().getEmpresa().getId(), form.getCuentaDestinoId());
                if (destino != null && (!destino.isActiva() || form.getMedio() == MedioPago.YAPE && destino.getCelularYape() == null))
                    throw new ReglaNegocioException("Selecciona una cuenta activa y habilitada para ese medio de pago.");
                String banco = destino == null ? "" : destino.getBanco();
                pago.setCuentaDestino(destino);
                String clave = form.getMedio() + "|" + (form.getMedio() == MedioPago.YAPE ? "" : banco.toUpperCase(java.util.Locale.ROOT))
                        + "|" + referencia.toUpperCase(java.util.Locale.ROOT);
                if (!referencia.isBlank() && !referencias.add(clave)) throw new ReglaNegocioException("Hay códigos de operación repetidos en los pagos.");
                pago.setReferencia(referencia);
                pago.setBanco(banco.isBlank() ? null : banco);
                digital = digital.add(pago.getRecibido());
            }
            recibido = recibido.add(pago.getRecibido());
            pagos.add(pago);
        }
        if (digital.compareTo(doc.getTotal()) > 0) throw new ReglaNegocioException("Los pagos digitales superan el total. Revisa los abonos; ese exceso no es vuelto en efectivo.");
        if (recibido.compareTo(new BigDecimal("9999999999999999.99")) > 0) throw new ReglaNegocioException("El monto recibido supera el máximo permitido.");
        BigDecimal efectivoPendiente = doc.getTotal().subtract(digital);
        for (PagoVenta pago : pagos) {
            if (pago.getMedio() == MedioPago.EFECTIVO) {
                pago.setAplicado(pago.getRecibido().min(efectivoPendiente));
                efectivoPendiente = efectivoPendiente.subtract(pago.getAplicado());
            } else pago.setAplicado(pago.getRecibido());
        }
        doc.getPagos().clear();
        doc.getPagos().addAll(pagos);
        doc.setVuelto(recibido.subtract(doc.getTotal()).max(BigDecimal.ZERO));
    }

    private void exigirPagoCompleto(DocumentoVenta doc) {
        if (doc.getTotal().signum() <= 0 || doc.getPagos().isEmpty() || doc.getSaldo().signum() > 0) {
            throw new ReglaNegocioException("Completa el pago antes de revisar o confirmar la venta.");
        }
        for (PagoVenta pago : doc.getPagos()) {
            if (pago.getMedio() != MedioPago.EFECTIVO && (pago.getCuentaDestino() == null || !pago.getCuentaDestino().isActiva()
                    || pago.getMedio() == MedioPago.YAPE && pago.getCuentaDestino().getCelularYape() == null))
                throw new ReglaNegocioException("Selecciona la cuenta de destino activa para cada pago digital en Corregir datos.");
            if (pago.getRecibido().signum() <= 0) throw new ReglaNegocioException("Ingresa el monto de cada pago o elimina la fila vacía.");
            if (pago.getMedio() != MedioPago.EFECTIVO && (pago.getReferencia() == null || pago.getReferencia().isBlank())) {
                throw new ReglaNegocioException("Indica el código de operación de cada Yape o transferencia.");
            }
            if (pago.getMedio() == MedioPago.TRANSFERENCIA && (pago.getBanco() == null || pago.getBanco().isBlank())) {
                throw new ReglaNegocioException("Indica el banco de la transferencia.");
            }
        }
    }

    public DocumentoVenta ver(Long sedeId, Long id) {
        DocumentoVenta doc = buscar(sedeId, id);
        doc.getLineas().size();
        doc.getPagos().size();
        if (doc.getConfirmadoPor() != null) doc.getConfirmadoPor().getNombre();
        if (doc.getAnuladoPor() != null) doc.getAnuladoPor().getNombre();
        return doc;
    }

    @Transactional
    public void descartarPendiente(Long sedeId, Long usuarioId, Long id, Long version) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        autorizarYBloquear(usuario, sedeId);
        DocumentoVenta doc = documentos.bloquear(id, sedeId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento no encontrado en esta sede."));
        if (doc.getEstado() != EstadoDocumentoVenta.BORRADOR || doc.getCorrelativo() != null || doc.getConfirmadoEn() != null) {
            throw new ReglaNegocioException("Solo se pueden descartar documentos pendientes sin confirmar.");
        }
        if (!Objects.equals(doc.getVersion(), version)) {
            throw new ReglaNegocioException("El documento cambió; vuelve a abrirlo antes de descartarlo.");
        }
        documentos.delete(doc);
    }

    public DocumentoVenta paraImprimir(Long sedeId, Long id) {
        DocumentoVenta doc = ver(sedeId, id);
        if (doc.getConfirmadoEn() == null || doc.getEstado() == EstadoDocumentoVenta.BORRADOR) {
            throw new ReglaNegocioException("Confirma la venta o emite la cotización antes de imprimir su documento numerado.");
        }
        doc.getSede().getNombre();
        doc.getSede().getEmpresa().getNombreComercial();
        return doc;
    }

    /**
     * Anula una venta confirmada y repone sus existencias en la misma sede. Conserva el historial de pagos; no ejecuta devoluciones bancarias.
     *
     * @param sedeId identificador de la sede de trabajo autorizada
     * @param usuarioId identificador del usuario que realiza la operación
     * @param id identificador del documento de esta sede
     * @param version versión presentada por el usuario al revisar el documento
     * @param motivo justificación de la anulación
     * @return venta anulada
     * @throws sistema_maderera.exception.ReglaNegocioException si los datos, permisos o estado no permiten la operación
     */
    @Transactional
    public DocumentoVenta anular(Long sedeId, Long usuarioId, Long id, Long version, String motivo) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        autorizarYBloquear(usuario, sedeId);
        DocumentoVenta doc = documentos.bloquear(id, sedeId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento no encontrado en esta sede."));
        if (doc.getEstado() != EstadoDocumentoVenta.CONFIRMADO) throw new ReglaNegocioException("Solo se puede anular una venta confirmada; no se puede anular dos veces.");
        if (!Objects.equals(version, doc.getVersion())) throw new ReglaNegocioException("El documento cambió. Vuelve a revisarlo.");
        String razon = motivo == null ? "" : motivo.trim();
        if (razon.isEmpty() || razon.length() > 255) throw new ReglaNegocioException("Indica el motivo de anulación, de 1 a 255 caracteres.");
        var porInventario = new java.util.TreeMap<Long, DetalleVenta>();
        for (DetalleVenta linea : doc.getLineas()) {
            Long inventarioId = inventarios.idPorProductoYSede(linea.getProducto().getId(), sedeId)
                    .orElseThrow(() -> new ReglaNegocioException("No se encontró el inventario original; no se anuló la venta."));
            porInventario.put(inventarioId, linea);
        }
        LocalDateTime ahora = LocalDateTime.now(clock);
        for (var entrada : porInventario.entrySet()) {
            Inventario inventario = inventarios.buscarParaActualizar(entrada.getKey(), sedeId).orElseThrow();
            DetalleVenta linea = entrada.getValue();
            if (!inventario.getProducto().getUnidadMedida().equals(linea.getUnidadMedida())) {
                throw new ReglaNegocioException("La unidad del producto cambió; revísala antes de reponer las existencias.");
            }
            BigDecimal anterior = inventario.getStock();
            BigDecimal posterior = anterior.add(linea.getCantidad());
            if (posterior.compareTo(new BigDecimal("99999999.9999")) > 0) {
                throw new ReglaNegocioException("La reposición supera el stock máximo permitido. No se anuló la venta.");
            }
            inventario.setStock(posterior);
            MovimientoInventario movimiento = new MovimientoInventario();
            movimiento.setInventario(inventario);
            movimiento.setUsuario(usuario);
            movimiento.setTipo(TipoMovimiento.ENTRADA);
            movimiento.setCantidad(linea.getCantidad());
            movimiento.setStockAnterior(anterior);
            movimiento.setStockPosterior(posterior);
            movimiento.setMotivo("Anulación " + doc.getCodigoDocumento() + " (documento " + doc.getId() + ")");
            movimiento.setFecha(ahora);
            movimientos.save(movimiento);
        }
        for (PagoVenta pago : doc.getPagos()) pago.setRevertido(pago.getAplicado());
        doc.setEstado(EstadoDocumentoVenta.ANULADO);
        doc.setAnuladoEn(ahora);
        doc.setAnuladoPor(usuario);
        doc.setMotivoAnulacion(razon);
        doc.setActualizado(ahora);
        return documentos.save(doc);
    }

    @Transactional
    public DocumentoVenta copiarAnulado(Long sedeId, Long usuarioId, Long id, Long version) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        Sede sede = autorizarYBloquear(usuario, sedeId);
        DocumentoVenta origen = documentos.bloquear(id, sedeId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento no encontrado en esta sede."));
        if (origen.getEstado() != EstadoDocumentoVenta.ANULADO) throw new ReglaNegocioException("Copia los datos desde un documento anulado.");
        if (!Objects.equals(version, origen.getVersion())) throw new ReglaNegocioException("El documento cambió. Vuelve a abrirlo.");
        var pendiente = documentos.findFirstByDocumentoOrigenIdAndEstadoOrderByIdDesc(id, EstadoDocumentoVenta.BORRADOR);
        if (pendiente.isPresent()) return pendiente.get();
        return documentos.save(copiarDatos(origen, sede, usuario));
    }

    private DocumentoVenta copiarDatos(DocumentoVenta origen, Sede sede, Usuario usuario) {
        DocumentoVenta copia = new DocumentoVenta();
        copia.setDocumentoOrigenId(origen.getId());
        copia.setCodigoOrigen(origen.getCodigoDocumento());
        copia.setSede(sede);
        copia.setUsuario(usuario);
        copia.setTipo(origen.getTipo());
        copia.setCliente(origen.getCliente());
        copia.setClienteNombre(origen.getClienteNombre());
        copia.setClienteDocumento(origen.getClienteDocumento());
        copia.setClienteDireccion(origen.getClienteDireccion());
        copia.setDetalles(origen.getDetalles());
        copia.setTotal(origen.getTotal());
        copia.setSubtotal(origen.getSubtotal());
        copia.setIgv(origen.getIgv());
        copia.setTasaIgv(origen.getTasaIgv());
        copia.setVuelto(origen.getVuelto());
        copia.setFecha(LocalDateTime.now(clock));
        copia.setActualizado(copia.getFecha());
        for (DetalleVenta original : origen.getLineas()) {
            DetalleVenta linea = new DetalleVenta();
            linea.setDocumento(copia);
            linea.setProducto(original.getProducto());
            linea.setCodigo(original.getCodigo());
            linea.setNombre(original.getNombre());
            linea.setUnidadMedida(original.getUnidadMedida());
            linea.setCantidad(original.getCantidad());
            linea.setPrecio(original.getPrecio());
            linea.setImporteCalculado(original.getImporteCalculado());
            linea.setImporteCobrar(original.getImporteCobrar());
            copia.getLineas().add(linea);
        }
        for (PagoVenta original : origen.getPagos()) {
            PagoVenta pago = new PagoVenta();
            pago.setDocumento(copia);
            pago.setMedio(original.getMedio());
            pago.setRecibido(original.getRecibido());
            pago.setAplicado(original.getAplicado());
            pago.setReferencia(original.getReferencia());
            pago.setBanco(original.getBanco());
            pago.setCuentaDestino(original.getCuentaDestino());
            copia.getPagos().add(pago);
        }
        return copia;
    }

    /**
     * Emite una cotización pendiente con numeración propia. No registra cobros ni reserva o descuenta stock.
     *
     * @param sedeId identificador de la sede de trabajo autorizada
     * @param usuarioId identificador del usuario que realiza la operación
     * @param id identificador del documento de esta sede
     * @param version versión presentada por el usuario al revisar el documento
     * @return cotización emitida
     * @throws sistema_maderera.exception.ReglaNegocioException si los datos, permisos o estado no permiten la operación
     */
    @Transactional
    public DocumentoVenta emitirCotizacion(Long sedeId, Long usuarioId, Long id, Long version) {
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow();
        autorizarYBloquear(usuario, sedeId);
        DocumentoVenta doc = documentos.bloquear(id, sedeId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento no encontrado en esta sede."));
        exigirBorrador(doc);
        if (doc.getTipo() != TipoDocumentoVenta.COTIZACION || !Objects.equals(version, doc.getVersion())) {
            throw new ReglaNegocioException("Revisa la cotización antes de emitirla.");
        }
        if (doc.getLineas().isEmpty() || doc.getTotal().signum() <= 0) throw new ReglaNegocioException("Agrega productos con un total mayor que cero.");
        Cliente cliente = doc.getCliente();
        if (cliente != null && !cliente.getEstado()) throw new ReglaNegocioException("El cliente está inactivo.");
        for (DetalleVenta linea : doc.getLineas()) {
            Producto p = linea.getProducto();
            if (!p.getEstado() || !p.getCategoria().getEstado() || !p.getUnidadMedida().equals(linea.getUnidadMedida())) {
                throw new ReglaNegocioException("Revisa el producto y su unidad antes de emitir: " + linea.getNombre());
            }
        }
        var numero = numeracion.siguiente(sedeId, TipoDocumentoVenta.COTIZACION);
        doc.setSerie(numero.serie());
        doc.setCorrelativo(numero.correlativo());
        doc.setClienteNombre(cliente == null ? "Sin cliente identificado" : cliente.getNombre());
        doc.setClienteDocumento(cliente == null ? null : cliente.getTipoIdentificacion() + (cliente.getNumeroDocumento() == null ? "" : " " + cliente.getNumeroDocumento()));
        doc.setClienteDireccion(cliente == null ? null : cliente.getDireccion());
        doc.getPagos().clear();
        doc.setVuelto(BigDecimal.ZERO);
        doc.setConfirmadoPor(usuario);
        doc.setConfirmadoEn(LocalDateTime.now(clock));
        doc.setActualizado(doc.getConfirmadoEn());
        doc.setEstado(EstadoDocumentoVenta.EMITIDO);
        return documentos.save(doc);
    }

    public DocumentoVenta ventaDeCotizacion(Long sedeId, Long id) {
        buscar(sedeId, id);
        return documentos.findFirstByDocumentoOrigenIdOrderByIdDesc(id).orElse(null);
    }

    @Transactional
    public DocumentoVenta convertirCotizacion(Long sedeId, Long usuarioId, Long id, Long version, TipoDocumentoVenta tipo) {
        Usuario usuario = usuarios.findById(usuarioId).orElseThrow();
        Sede sede = autorizarYBloquear(usuario, sedeId);
        DocumentoVenta origen = documentos.bloquear(id, sedeId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento no encontrado en esta sede."));
        if (origen.getTipo() != TipoDocumentoVenta.COTIZACION || origen.getEstado() != EstadoDocumentoVenta.EMITIDO
                || !Objects.equals(version, origen.getVersion())) throw new ReglaNegocioException("Selecciona una cotización emitida y actualizada.");
        if (tipo == null || tipo == TipoDocumentoVenta.COTIZACION) throw new ReglaNegocioException("Elige boleta, factura o nota de venta.");
        var existente = documentos.findFirstByDocumentoOrigenIdOrderByIdDesc(id);
        if (existente.isPresent()) return existente.get();
        DocumentoVenta copia = copiarDatos(origen, sede, usuario);
        copia.setTipo(tipo);
        copia.getPagos().clear();
        copia.setVuelto(BigDecimal.ZERO);
        return documentos.save(copia);
    }

    /**
     * Confirma una venta, le asigna un número y descuenta los productos del stock.
     * Requiere el pago completo y existencias suficientes.
     *
     * @param sedeId identificador de la sede de trabajo autorizada
     * @param usuarioId identificador del usuario que realiza la operación
     * @param id identificador del documento de esta sede
     * @param version versión revisada, para detectar si el documento cambió
     * @return venta confirmada
     * @throws sistema_maderera.exception.ReglaNegocioException si los datos, permisos o estado no permiten la operación
     */
    @Transactional
    public DocumentoVenta confirmar(Long sedeId, Long usuarioId, Long id, Long version) {
        Usuario usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));
        autorizarYBloquear(usuario, sedeId);
        DocumentoVenta doc = documentos.bloquear(id, sedeId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento no encontrado en esta sede."));
        exigirBorrador(doc);
        if (!Objects.equals(version, doc.getVersion())) throw new ReglaNegocioException("El documento cambió. Revísalo nuevamente antes de confirmar.");
        if (doc.getTipo() == TipoDocumentoVenta.COTIZACION) throw new ReglaNegocioException("Una cotización no se confirma como venta ni descuenta stock.");
        if (doc.getLineas().isEmpty() || doc.getTotal().signum() <= 0) throw new ReglaNegocioException("Agrega productos con un total mayor que cero.");
        exigirPagoCompleto(doc);
        Cliente cliente = doc.getCliente();
        if (cliente != null && !cliente.getEstado()) throw new ReglaNegocioException("El cliente está inactivo.");
        if (doc.getTipo() == TipoDocumentoVenta.FACTURA && (cliente == null || cliente.getTipoIdentificacion() != TipoIdentificacion.RUC)) {
            throw new ReglaNegocioException("Para factura se requiere un cliente con RUC.");
        }
        var porInventario = new java.util.TreeMap<Long, DetalleVenta>();
        for (DetalleVenta linea : doc.getLineas()) {
            Long inventarioId = inventarios.idPorProductoYSede(linea.getProducto().getId(), sedeId)
                    .orElseThrow(() -> new ReglaNegocioException("Un producto ya no está registrado en la sede."));
            porInventario.put(inventarioId, linea);
        }
        var numero = numeracion.siguiente(sedeId, doc.getTipo());
        LocalDateTime ahora = LocalDateTime.now(clock);
        for (var entrada : porInventario.entrySet()) {
            Inventario inventario = inventarios.buscarParaActualizar(entrada.getKey(), sedeId).orElseThrow();
            DetalleVenta linea = entrada.getValue();
            Producto producto = inventario.getProducto();
            if (!producto.getEstado() || !producto.getCategoria().getEstado()) throw new ReglaNegocioException("Producto o categoría inactivos: " + producto.getNombre());
            if (!producto.getUnidadMedida().equals(linea.getUnidadMedida())) throw new ReglaNegocioException("La unidad del producto cambió. Vuelve a editar el documento.");
            BigDecimal anterior = inventario.getStock();
            BigDecimal posterior = anterior.subtract(linea.getCantidad());
            if (posterior.signum() < 0) throw new ReglaNegocioException("Stock insuficiente para " + producto.getNombre() + ".");
            inventario.setStock(posterior);
            MovimientoInventario movimiento = new MovimientoInventario();
            movimiento.setInventario(inventario);
            movimiento.setUsuario(usuario);
            movimiento.setTipo(TipoMovimiento.SALIDA);
            movimiento.setCantidad(linea.getCantidad().negate());
            movimiento.setStockAnterior(anterior);
            movimiento.setStockPosterior(posterior);
            movimiento.setMotivo("Venta " + numero.getCodigo() + " (documento " + doc.getId() + ")");
            movimiento.setFecha(ahora);
            movimientos.save(movimiento);
        }
        doc.setClienteNombre(cliente == null ? "Sin cliente identificado" : cliente.getNombre());
        doc.setClienteDocumento(cliente == null ? null : cliente.getTipoIdentificacion() + (cliente.getNumeroDocumento() == null ? "" : " " + cliente.getNumeroDocumento()));
        doc.setClienteDireccion(cliente == null ? null : cliente.getDireccion());
        doc.setSerie(numero.serie());
        doc.setCorrelativo(numero.correlativo());
        doc.setConfirmadoEn(ahora);
        doc.setConfirmadoPor(usuario);
        doc.setActualizado(ahora);
        doc.setEstado(EstadoDocumentoVenta.CONFIRMADO);
        return documentos.save(doc);
    }

    public record ProductoVenta(Long id, String codigo, String nombre, String unidad, BigDecimal stock,
            BigDecimal minimo, BigDecimal precio, boolean activo) { }

    public List<ProductoVenta> productosDisponibles(Long sedeId) {
        return inventarios.listarPorSede(sedeId).stream().map(i -> {
            Producto p = i.getProducto();
            return new ProductoVenta(p.getId(), p.getCodigo(), p.getNombre(), p.getUnidadMedida(),
                    i.getStock().stripTrailingZeros(), i.getStockMinimo().stripTrailingZeros(),
                    p.getPrecioVenta(), Boolean.TRUE.equals(p.getEstado()) && Boolean.TRUE.equals(p.getCategoria().getEstado()));
        }).toList();
    }

    private List<DetalleVenta> prepararLineas(Long sedeId, DocumentoVentaForm form) {
        List<DetalleVenta> resultado = new ArrayList<>();
        var usados = new HashSet<Long>();
        for (DetalleVentaForm item : form.getLineas()) {
            if (!usados.add(item.getProductoId())) throw new ReglaNegocioException("El producto está repetido; modifica su cantidad en la misma fila.");
            Inventario inventario = inventarios.findByProductoIdAndSedeId(item.getProductoId(), sedeId)
                    .orElseThrow(() -> new ReglaNegocioException("Selecciona productos registrados en esta sede."));
            Producto producto = inventario.getProducto();
            if (!Boolean.TRUE.equals(producto.getEstado()) || !Boolean.TRUE.equals(producto.getCategoria().getEstado())) {
                throw new ReglaNegocioException("El producto o su categoría están inactivos: " + producto.getNombre());
            }
            if ("UND".equals(producto.getUnidadMedida()) && item.getCantidad().stripTrailingZeros().scale() > 0) {
                throw new ReglaNegocioException("Los productos en unidades requieren cantidades enteras.");
            }
            BigDecimal calculado = item.getCantidad().multiply(item.getPrecio()).setScale(2, RoundingMode.HALF_UP);
            if (calculado.compareTo(new BigDecimal("99999999999999.99")) > 0) {
                throw new ReglaNegocioException("El importe calculado supera el máximo permitido.");
            }
            DetalleVenta linea = new DetalleVenta();
            linea.setProducto(producto);
            linea.setCodigo(producto.getCodigo());
            linea.setNombre(producto.getNombre());
            linea.setUnidadMedida(producto.getUnidadMedida());
            linea.setCantidad(item.getCantidad());
            linea.setPrecio(item.getPrecio());
            linea.setImporteCalculado(calculado);
            linea.setImporteCobrar(item.getImporteCobrar() == null ? calculado : item.getImporteCobrar().setScale(2));
            resultado.add(linea);
        }
        return resultado;
    }

    private DocumentoVenta buscar(Long sedeId, Long id) {
        return documentos.findByIdAndSedeId(id, sedeId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento no encontrado en esta sede."));
    }

    private void exigirBorrador(DocumentoVenta doc) {
        if (doc.getEstado() != EstadoDocumentoVenta.BORRADOR) {
            throw new ReglaNegocioException("Solo se pueden editar documentos en borrador.");
        }
    }
}
