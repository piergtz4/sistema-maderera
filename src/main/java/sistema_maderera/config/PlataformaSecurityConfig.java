package sistema_maderera.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;

import sistema_maderera.service.PlataformaAuthService;

@Configuration
public class PlataformaSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain plataformaFilterChain(
            HttpSecurity http,
            PlataformaAuthService usuarios,
            PasswordEncoder encoder) throws Exception {

        var proveedor = new DaoAuthenticationProvider(usuarios);
        proveedor.setPasswordEncoder(encoder);

        http
            .securityMatcher("/plataforma", "/plataforma/**")
            .authenticationManager(new ProviderManager(proveedor))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/plataforma/login").permitAll()
                .anyRequest().hasRole("ADMINISTRADOR_PLATAFORMA"))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(
                    new LoginUrlAuthenticationEntryPoint(
                        "/plataforma/login")))
            .formLogin(form -> form
                .loginPage("/plataforma/login")
                .loginProcessingUrl("/plataforma/login")
                .usernameParameter("usuario")
                .defaultSuccessUrl("/plataforma", true)
                .failureUrl("/plataforma/login?error")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/plataforma/logout")
                .logoutSuccessUrl("/plataforma/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"));

        return http.build();
    }
}