package sistema_maderera.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import sistema_maderera.entity.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    Optional<Categoria> findByCodigoIgnoreCase(String codigo);
    Optional<Categoria> findByNombreIgnoreCase(String nombre);
    List<Categoria> findAllByOrderByNombreAsc();
    List<Categoria> findByEstadoTrueOrderByNombreAsc();
}
