package sistema_maderera.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sistema_maderera.entity.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    @EntityGraph(attributePaths = "categoria")
    List<Producto> findByEmpresaIdOrderByNombreAsc(Long empresaId);

    @Query("select count(p) from Producto p where p.empresa.id = "
            + "(select s.empresa.id from Sede s where s.id = :sedeId)")
    long contarPorEmpresaDeSede(Long sedeId);

    @EntityGraph(attributePaths = "categoria")
    Optional<Producto> findByIdAndEmpresaId(Long id, Long empresaId);

    @Override
    @EntityGraph(attributePaths = "categoria")
    Optional<Producto> findById(Long id);
}
