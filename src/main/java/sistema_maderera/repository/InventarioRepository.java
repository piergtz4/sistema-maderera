package sistema_maderera.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import sistema_maderera.entity.Inventario;

public interface InventarioRepository extends JpaRepository<Inventario, Long> {
    @Query("select i.id from Inventario i where i.producto.id = :productoId and i.sede.id = :sedeId")
    Optional<Long> idPorProductoYSede(Long productoId, Long sedeId);
    @Query("select i from Inventario i join fetch i.producto p join fetch p.categoria join fetch i.sede "
            + "where i.sede.id = :sedeId order by p.nombre")
    List<Inventario> listarPorSede(Long sedeId);

    @Query("select i from Inventario i join fetch i.producto join fetch i.sede "
            + "order by i.producto.id, i.sede.nombre")
    List<Inventario> listarSedesRegistradas();

    boolean existsByProductoIdAndSedeId(Long productoId, Long sedeId);

    Optional<Inventario> findByProductoIdAndSedeId(Long productoId, Long sedeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventario i where i.producto.id = :productoId and i.sede.id = :sedeId")
    Optional<Inventario> buscarProductoParaActualizar(Long productoId, Long sedeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventario i join fetch i.producto where i.id = :id and i.sede.id = :sedeId")
    Optional<Inventario> buscarParaActualizar(Long id, Long sedeId);
}
