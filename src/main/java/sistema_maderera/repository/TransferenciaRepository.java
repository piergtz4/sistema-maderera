package sistema_maderera.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import sistema_maderera.entity.Transferencia;

public interface TransferenciaRepository extends JpaRepository<Transferencia, Long> {
    @EntityGraph(attributePaths = {"salida.inventario.producto", "salida.inventario.sede",
            "salida.usuario", "entrada.inventario.sede"})
    List<Transferencia> findBySalidaInventarioSedeIdOrEntradaInventarioSedeIdOrderByIdDesc(
            Long sedeOrigenId, Long sedeDestinoId);
}
