package sistema_maderera.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import sistema_maderera.entity.Sede;
import java.util.Optional;
import java.util.List;

public interface SedeRepository extends JpaRepository<Sede, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sede s where s.id = :id")
    Optional<Sede> bloquearParaNumeracion(Long id);
    Optional<Sede> findFirstByOrderByIdAsc();
    List<Sede> findByEmpresaIdOrderByEstadoDescNombreAsc(Long empresaId);
    List<Sede> findByEmpresaIdAndEstadoTrueOrderByNombreAsc(Long empresaId);
    Optional<Sede> findByIdAndEmpresaId(Long id, Long empresaId);
    Optional<Sede> findByIdAndEmpresaIdAndEstadoTrue(Long id, Long empresaId);
    Optional<Sede> findByEmpresaIdAndNombreIgnoreCase(Long empresaId, String nombre);
    long countByEmpresaIdAndEstadoTrue(Long empresaId);
    Optional<Sede> findByEmpresaIdAndCodigoDocumentos(Long empresaId, Integer codigoDocumentos);
    @Query("select coalesce(max(s.codigoDocumentos), 0) from Sede s where s.empresa.id = :empresaId")
    int ultimoCodigoDocumentos(Long empresaId);
}
