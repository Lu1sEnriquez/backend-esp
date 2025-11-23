package com.api.plant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Inyectamos la URL. Si son varias separadas por coma, podemos parsearlas.
    @Value("${frontend.url}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // 1. ACTIVAR CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 2. Desactivar CSRF (API Stateless)
                .csrf(AbstractHttpConfigurer::disable)
                // 3. Stateless Session
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 4. Rutas Públicas y Privadas
                .authorizeHttpRequests(auth -> auth
                        // Acceso libre a MQTT auth, Login/Registro y WebSockets
                        .requestMatchers("/api/mqtt/**", "/api/auth/**", "/ws/**").permitAll()
                        // Todo lo demás requiere autenticación
                        .anyRequest().authenticated()
                )
                // 5. Basic Auth
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // TRUCO: Soportar múltiples orígenes si frontendUrl viene separado por comas
        // Ejemplo de valor: "http://localhost:3000,http://189.197.116.236:3000"
        List<String> allowedOrigins = Arrays.asList(frontendUrl.split(","));

        configuration.setAllowedOrigins(allowedOrigins);

        // Métodos permitidos
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // Headers permitidos (Authorization es vital para Basic Auth)
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin"));

        // Exponer headers si el front necesita leer algo específico
        configuration.setExposedHeaders(List.of("Authorization"));

        // Credenciales (Cookies/Auth Headers)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}