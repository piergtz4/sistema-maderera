package sistema_maderera.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import sistema_maderera.service.DemoDataService;

@Configuration
public class DataInitializer {
    @Bean
    @ConditionalOnProperty(name = "maderpro.demo.enabled", havingValue = "true")
    CommandLineRunner inicializarDatos(DemoDataService servicio,
            @Value("${maderpro.demo.password:}") String password) {
        return args -> servicio.inicializar(password);
    }
}
