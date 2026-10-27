package sistema_maderera.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import sistema_maderera.entity.Empresa;

public interface EmpresaRepository extends JpaRepository<Empresa, Long> {
    Optional<Empresa> findFirstByOrderByIdAsc();

    @Query("select e.nombreComercial from Empresa e where e.id = :id")
    Optional<String> buscarNombre(Long id);

    boolean existsByIdAndLogoDatosIsNotNull(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Empresa e where e.id = :id")
    Optional<Empresa> bloquearParaUsuarios(Long id);
}
