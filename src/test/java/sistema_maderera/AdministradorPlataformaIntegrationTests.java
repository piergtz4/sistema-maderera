package sistema_maderera;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import sistema_maderera.dto.AdministradorPlataformaForm;
import sistema_maderera.repository.AdministradorPlataformaRepository;
import sistema_maderera.service.AdministradorPlataformaService;
import sistema_maderera.exception.ReglaNegocioException;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdministradorPlataformaIntegrationTests {

    @Autowired AdministradorPlataformaService servicio;
    @Autowired AdministradorPlataformaRepository repositorio;
    @Autowired PasswordEncoder encoder;
    @Autowired EntityManager entityManager;

    @Test // Verifica el registro de la cuenta y la contraseña protegida con bcrypt
    void creaAdministrador() {
        AdministradorPlataformaForm form = new AdministradorPlataformaForm();
        form.setNombre("  Administrador de prueba  ");
        form.setUsuario("Admin.Plataforma");
        form.setPassword("ClavePlataforma123");

        var creado = servicio.crear(form);

        entityManager.flush();
        entityManager.clear();

        var guardado = repositorio.findById(creado.getId()).orElseThrow();

        assertThat(guardado.getNombre()).isEqualTo("Administrador de prueba");
        assertThat(guardado.getUsuario()).isEqualTo("admin.plataforma");
        assertThat(guardado.getEstado()).isTrue();
        assertThat(guardado.getPassword()).isNotEqualTo(form.getPassword());
        assertThat(encoder.matches(
                form.getPassword(), guardado.getPassword())).isTrue();
    }

    @Test // Comprueba que no se registre un usuario repetido
    void rechazaUsuarioRepetido() {
        long cantidadInicial = repositorio.count();

        AdministradorPlataformaForm form = new AdministradorPlataformaForm();
        form.setNombre("Administrador de prueba");
        form.setUsuario("admin.plataforma");
        form.setPassword("ClavePlataforma123");

        servicio.crear(form);

        form.setUsuario("ADMIN.PLATAFORMA");

        assertThatThrownBy(() -> servicio.crear(form))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessage("Ese nombre de usuario ya está registrado.");

        assertThat(repositorio.count()).isEqualTo(cantidadInicial + 1);
    }
    @Test // Comprueba que se rechace una contraseña sin numeros
    void rechazaContrasenaInvalida() {
        long cantidadInicial = repositorio.count();

        AdministradorPlataformaForm form = new AdministradorPlataformaForm();
        form.setNombre("Administrador de prueba");
        form.setUsuario("admin.plataforma");
        form.setPassword("SoloLetrasSinNumeros");

        assertThatThrownBy(() -> servicio.crear(form))
                .isInstanceOf(ReglaNegocioException.class)
                .hasMessageContaining("letras y números");

        assertThat(repositorio.count()).isEqualTo(cantidadInicial);
    }
}