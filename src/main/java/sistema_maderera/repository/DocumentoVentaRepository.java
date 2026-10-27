package sistema_maderera.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import sistema_maderera.entity.DocumentoVenta;

public interface DocumentoVentaRepository extends JpaRepository<DocumentoVenta, Long> {
    @org.springframework.data.jpa.repository.Query("select p.medio as medio, p.banco as banco, "
            + "sum(p.aplicado - p.revertido) as monto, count(p) as pagos from PagoVenta p join p.documento d "
            + "where d.sede.id = :sedeId and d.estado = sistema_maderera.entity.EstadoDocumentoVenta.CONFIRMADO "
            + "and d.tipo <> sistema_maderera.entity.TipoDocumentoVenta.COTIZACION "
            + "and d.confirmadoEn >= :desde and d.confirmadoEn < :hasta "
            + "and p.aplicado > p.revertido group by p.medio, p.banco")
    List<ResumenCobro> cobrosDelPeriodo(Long sedeId, java.time.LocalDateTime desde, java.time.LocalDateTime hasta);

    interface ResumenCobro {
        sistema_maderera.entity.MedioPago getMedio();
        String getBanco();
        java.math.BigDecimal getMonto();
        long getPagos();
    }

    @org.springframework.data.jpa.repository.Query("select distinct d from DocumentoVenta d left join fetch d.lineas where d.sede.id = :sedeId "
            + "and d.estado = sistema_maderera.entity.EstadoDocumentoVenta.CONFIRMADO "
            + "and d.tipo <> sistema_maderera.entity.TipoDocumentoVenta.COTIZACION "
            + "and d.confirmadoEn >= :desde and d.confirmadoEn < :hasta order by d.confirmadoEn")
    List<DocumentoVenta> ventasDelPeriodo(Long sedeId, java.time.LocalDateTime desde, java.time.LocalDateTime hasta);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select d from DocumentoVenta d where d.id = :id and d.sede.id = :sedeId")
    Optional<DocumentoVenta> bloquear(Long id, Long sedeId);
    boolean existsBySedeIdAndCorrelativoIsNotNull(Long sedeId);
    Optional<DocumentoVenta> findFirstByDocumentoOrigenIdOrderByIdDesc(Long documentoOrigenId);
    Optional<DocumentoVenta> findFirstByDocumentoOrigenIdAndEstadoOrderByIdDesc(Long documentoOrigenId,
            sistema_maderera.entity.EstadoDocumentoVenta estado);
    @EntityGraph(attributePaths = {"cliente", "usuario"})
    List<DocumentoVenta> findBySedeIdOrderByActualizadoDescIdDesc(Long sedeId);
    @EntityGraph(attributePaths = {"cliente", "usuario"})
    Optional<DocumentoVenta> findByIdAndSedeId(Long id, Long sedeId);
}
