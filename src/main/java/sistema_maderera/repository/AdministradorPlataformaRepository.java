package sistema_maderera.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import sistema_maderera.entity.AdministradorPlataforma;

public interface AdministradorPlataformaRepository
        extends JpaRepository<AdministradorPlataforma, Long> {

    Optional<AdministradorPlataforma> findByUsuario(String usuario);
}