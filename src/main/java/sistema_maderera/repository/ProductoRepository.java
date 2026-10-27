package sistema_maderera.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import sistema_maderera.entity.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    @EntityGraph(attributePaths = "categoria")
    List<Producto> findAllByOrderByNombreAsc();

    @Override
    @EntityGraph(attributePaths = "categoria")
    Optional<Producto> findById(Long id);
}
