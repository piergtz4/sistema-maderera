package sistema_maderera.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import sistema_maderera.dto.AdministradorPlataformaForm;
import sistema_maderera.repository.AdministradorPlataformaRepository;
import sistema_maderera.service.AdministradorPlataformaService;

@Configuration
@Profile("!test")
public class PlataformaInitializer {

    @Bean
    @ConditionalOnProperty(
        name = "plataforma.admin.inicializar",
        havingValue = "true"
    )
    CommandLineRunner crearAdministradorInicial(
            AdministradorPlataformaService servicio,
            AdministradorPlataformaRepository repositorio,
            @Value("${plataforma.admin.nombre:}") String nombre,
            @Value("${plataforma.admin.usuario:}") String usuario,
            @Value("${plataforma.admin.password:}") String password) {

        return args -> {
            if (repositorio.count() > 0) {
                return;
            }

            AdministradorPlataformaForm form =
                    new AdministradorPlataformaForm();

            form.setNombre(nombre);
            form.setUsuario(usuario);
            form.setPassword(password);

            servicio.crear(form);
        };
    }
}