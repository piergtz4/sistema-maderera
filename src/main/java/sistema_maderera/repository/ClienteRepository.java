package sistema_maderera.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import sistema_maderera.entity.Cliente;
import sistema_maderera.entity.TipoIdentificacion;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    List<Cliente> findByEmpresaIdOrderByNombreAsc(Long empresaId);
    Optional<Cliente> findByIdAndEmpresaId(Long id, Long empresaId);
    Optional<Cliente> findByEmpresaIdAndTipoIdentificacionAndNumeroDocumento(
            Long empresaId, TipoIdentificacion tipo, String numeroDocumento);
}

