package sistema_maderera.repository;

import sistema_maderera.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

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

    @Query("select u from Usuario u where u.empresa.id = :empresaId and u.estado = true "
            + "and (u.sede.id = :sedeId or exists (select a.id from AccesoUsuarioSede a "
            + "where a.usuario = u and a.sede.id = :sedeId))")
    List<Usuario> findByEmpresaIdAndSedeIdAndEstadoTrue(Long empresaId, Long sedeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.empresa.id = :empresaId and u.rol = 'ADMINISTRADOR_GENERAL' order by u.id")
    List<Usuario> generalesParaEdicion(Long empresaId);

    long countByEmpresaIdAndRolAndEstadoTrue(Long empresaId, String rol);
    long countByEmpresaIdAndEstadoTrue(Long empresaId);
    boolean existsBySedeId(Long sedeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Usuario u where u.empresa.id = :empresaId and u.estado = true order by u.id")
    List<Usuario> activosParaCupo(Long empresaId);

    List<Usuario> findByEmpresaIdAndRolOrderByNombreAsc(Long empresaId, String rol);

    Optional<Usuario> findByIdAndEmpresaIdAndRol(Long id, Long empresaId, String rol);

    @Query("select u from Usuario u join fetch u.empresa left join fetch u.sede where u.id = :id")
    Optional<Usuario> buscarSesion(Long id);
}
