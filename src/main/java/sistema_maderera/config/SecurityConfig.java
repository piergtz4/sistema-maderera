package sistema_maderera.config;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import sistema_maderera.security.SesionVigenteFilter;
import sistema_maderera.service.UsuarioService;

@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UsuarioService usuarios)
            throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                .requestMatchers("/", "/login", "/error", "/css/**", "/js/**", "/img/**").permitAll()
                .requestMatchers("/configuracion/**").hasRole("ADMINISTRADOR_GENERAL")
                .requestMatchers("/sedes", "/sedes/**").hasRole("ADMINISTRADOR_GENERAL")
                .requestMatchers("/usuarios", "/usuarios/**").hasRole("ADMINISTRADOR_GENERAL")
                .requestMatchers(HttpMethod.POST, "/clientes/*/estado").hasRole("ADMINISTRADOR_GENERAL")
                .requestMatchers(HttpMethod.POST, "/proveedores/*/estado").hasRole("ADMINISTRADOR_GENERAL")
                .requestMatchers("/compras", "/compras/**", "/proveedores", "/proveedores/**")
                    .hasAnyRole("ADMINISTRADOR_GENERAL", "ADMINISTRADOR")
                .requestMatchers("/clientes", "/clientes/**", "/ventas", "/ventas/**")
                    .hasAnyRole("ADMINISTRADOR_GENERAL", "ADMINISTRADOR")
                .requestMatchers("/transferencias", "/transferencias/**").hasRole("ADMINISTRADOR_GENERAL")
                .requestMatchers("/seleccionar-sede", "/sede-trabajo").hasRole("ADMINISTRADOR_GENERAL")
                .requestMatchers(HttpMethod.POST, "/productos/*/inventario")
                    .hasAnyRole("ADMINISTRADOR_GENERAL", "ADMINISTRADOR")
                .requestMatchers(HttpMethod.POST, "/categorias/**", "/productos/**")
                    .hasRole("ADMINISTRADOR_GENERAL")
                .requestMatchers(HttpMethod.POST, "/movimientos").denyAll()
                .requestMatchers(HttpMethod.POST, "/inventario/**").denyAll()
                .requestMatchers("/categorias/nueva", "/categorias/editar/**",
                        "/productos/nuevo").hasRole("ADMINISTRADOR_GENERAL")
                .requestMatchers("/productos/editar/**")
                    .hasAnyRole("ADMINISTRADOR_GENERAL", "ADMINISTRADOR")
                .anyRequest().authenticated())
            .userDetailsService(usuarios)
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/")))
            .requestCache(cache -> cache.disable())
            .formLogin(form -> form
                .loginPage("/")
                .loginProcessingUrl("/login")
                .usernameParameter("usuario")
                .successHandler((request, response, authentication) -> {
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"success\":true,\"message\":\"Inicio de sesión correcto.\"}");
                })
                .failureHandler((request, response, exception) -> {
                    response.setStatus(401);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"success\":false,\"message\":"
                            + "\"El usuario o la contraseña son incorrectos.\"}");
                }))
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID"));
        http.addFilterBefore(new SesionVigenteFilter(usuarios), AuthorizationFilter.class);
        return http.build();
    }
}
