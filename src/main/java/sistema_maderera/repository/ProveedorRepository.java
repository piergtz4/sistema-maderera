package sistema_maderera.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import sistema_maderera.entity.Proveedor;
import sistema_maderera.entity.TipoIdentificacion;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {
    List<Proveedor> findByEmpresaIdOrderByNombreAsc(Long empresaId);
    Optional<Proveedor> findByIdAndEmpresaId(Long id, Long empresaId);
    Optional<Proveedor> findByEmpresaIdAndTipoIdentificacionAndNumeroDocumento(
            Long empresaId, TipoIdentificacion tipo, String numeroDocumento);
}

