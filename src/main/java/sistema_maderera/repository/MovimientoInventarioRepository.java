package sistema_maderera.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import sistema_maderera.entity.MovimientoInventario;

public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {
    @EntityGraph(attributePaths = {"inventario.producto", "usuario"})
    List<MovimientoInventario> findTop10ByInventarioSedeIdOrderByFechaDescIdDesc(Long sedeId);

    @EntityGraph(attributePaths = {"inventario.producto", "usuario"})
    List<MovimientoInventario> findByInventarioSedeIdOrderByFechaDescIdDesc(Long sedeId);
}
