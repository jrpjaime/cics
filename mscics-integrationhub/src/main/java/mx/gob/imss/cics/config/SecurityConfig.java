package mx.gob.imss.cics.config;


import mx.gob.imss.cics.filter.JwtRequestFilter;
import mx.gob.imss.cics.service.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

    @Autowired
    private JwtRequestFilter jwtRequestFilter;

    // Ahora sí usaremos esta referencia explícitamente
    @Autowired
    private CustomUserDetailsService userDetailsService;

    /**
     * Bean del codificador de contraseñas (BCrypt).
     * Se usa para comparar el password que llega del login contra el hash en Oracle.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CONFIGURACIÓN EXPLÍCITA DEL PROVEEDOR DE AUTENTICACIÓN.
     * Aquí es donde "usamos" el userDetailsService y eliminamos el warning.
     * Es la forma profesional de asegurar que Spring use nuestra tabla MSCC_USUARIO_CICS.
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        
        authProvider.setUserDetailsService(userDetailsService); // Vinculación explícita
        authProvider.setPasswordEncoder(passwordEncoder());    // Vinculación del algoritmo
        
        return authProvider;
    }

    /**
     * El Manager de autenticación se basa en los proveedores configurados arriba.
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(withDefaults())
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/mscics-integrationhub/v1/auth/**").permitAll() // Login abierto
                .requestMatchers("/mscics-integrationhub/v1/info").permitAll()   // Info abierta
                .anyRequest().authenticated() // Bloqueo total para el resto
            )
            // Agregamos el filtro JWT antes del filtro de usuario/password estándar
            .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class)
            // Política sin estado (Stateless) para microservicios
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Registramos nuestro proveedor explícito
            .authenticationProvider(authenticationProvider()); 

        return http.build();
    }
}
