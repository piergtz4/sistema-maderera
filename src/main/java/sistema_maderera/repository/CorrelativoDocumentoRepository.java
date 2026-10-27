package sistema_maderera.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import sistema_maderera.entity.CorrelativoDocumento;
import sistema_maderera.entity.TipoDocumentoVenta;

public interface CorrelativoDocumentoRepository extends JpaRepository<CorrelativoDocumento, Long> {
    boolean existsBySedeIdAndUltimoGreaterThan(Long sedeId, Long ultimo);
    Optional<CorrelativoDocumento> findBySedeIdAndTipo(Long sedeId, TipoDocumentoVenta tipo);
}
