package sistema_maderera.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import sistema_maderera.entity.CuentaEmpresa;

public interface CuentaEmpresaRepository extends JpaRepository<CuentaEmpresa, Long> {
    @org.springframework.data.jpa.repository.Query("select distinct c.banco from CuentaEmpresa c where c.activa = true "
            + "and c.empresa.id = (select s.empresa.id from Sede s where s.id = :sedeId) order by c.banco")
    List<String> bancosActivosPorSede(Long sedeId);

    List<CuentaEmpresa> findByEmpresaIdOrderByIdAsc(Long empresaId);
    Optional<CuentaEmpresa> findByIdAndEmpresaId(Long id, Long empresaId);
}
