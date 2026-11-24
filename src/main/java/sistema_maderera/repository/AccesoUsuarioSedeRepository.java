package sistema_maderera.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import sistema_maderera.entity.AccesoUsuarioSede;

public interface AccesoUsuarioSedeRepository extends JpaRepository<AccesoUsuarioSede, Long> {
    boolean existsBySedeId(Long sedeId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from AccesoUsuarioSede a where a.usuario.id = :usuarioId order by a.id")
    List<AccesoUsuarioSede> bloquearPermisos(Long usuarioId);
    @EntityGraph(attributePaths = {"sede", "sede.empresa"})
    List<AccesoUsuarioSede> findByUsuarioIdOrderBySedeNombreAsc(Long usuarioId);
    void deleteByUsuarioId(Long usuarioId);
}
