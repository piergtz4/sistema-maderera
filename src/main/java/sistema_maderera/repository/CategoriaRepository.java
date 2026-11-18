package sistema_maderera.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import sistema_maderera.entity.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Long> {
    Optional<Categoria> findByIdAndEmpresaId(Long id, Long empresaId);
    Optional<Categoria> findByEmpresaIdAndCodigoIgnoreCase(Long empresaId, String codigo);
    Optional<Categoria> findByEmpresaIdAndNombreIgnoreCase(Long empresaId, String nombre);
    List<Categoria> findByEmpresaIdOrderByNombreAsc(Long empresaId);
    List<Categoria> findByEmpresaIdAndEstadoTrueOrderByNombreAsc(Long empresaId);
}
