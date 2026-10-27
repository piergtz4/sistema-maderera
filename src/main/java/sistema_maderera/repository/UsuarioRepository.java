package sistema_maderera.repository;

import sistema_maderera.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @EntityGraph(attributePaths = {"empresa", "sede", "sede.empresa"})
    Optional<Usuario> findByUsuario(String usuario);

    @EntityGraph(attributePaths = {"empresa", "sede"})
    Optional<Usuario> findByUsuarioIgnoreCase(String usuario);

    @EntityGraph(attributePaths = {"sede"})
    List<Usuario> findByEmpresaIdOrderByNombreAsc(Long empresaId);

    @EntityGraph(attributePaths = {"sede"})
    Optional<Usuario> findByIdAndEmpresaId(Long id, Long empresaId);

    List<Usuario> findByEmpresaIdAndSedeIdAndEstadoTrue(Long empresaId, Long sedeId);

    long countByEmpresaIdAndRolAndEstadoTrue(Long empresaId, String rol);

    @Query("select u from Usuario u join fetch u.empresa left join fetch u.sede where u.id = :id")
    Optional<Usuario> buscarSesion(Long id);
}
