package sistema_maderera.repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import sistema_maderera.entity.*;

public interface CompraRepository extends JpaRepository<Compra, Long> {
    @EntityGraph(attributePaths = {"proveedor", "usuario"})
    List<Compra> findBySedeIdOrderByActualizadoDescIdDesc(Long sedeId);
    @EntityGraph(attributePaths = {"proveedor", "usuario", "sede", "confirmadoPor"})
    Optional<Compra> findByIdAndSedeId(Long id, Long sedeId);
    Optional<Compra> findByProveedorIdAndTipoAndReferencia(Long proveedorId, TipoComprobanteCompra tipo, String referencia);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Compra c where c.id = :id and c.sede.id = :sedeId")
    Optional<Compra> bloquear(Long id, Long sedeId);
}
