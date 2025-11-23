package com.api.plant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    @Value("${frontend.url}")
    private String frontendUrl;

    // En SecurityConfig.java

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // 1. ACTIVAR CORS (¡ESTO FALTABA!)
                // Le dice a Spring Security: "Usa la configuración de corsConfigurationSource que definí abajo"
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // --- ¡LA CORRECCIÓN ESTÁ AQUÍ! ---
                        // 1. Permite el acceso anónimo a los endpoints de autenticación de Mosquitto

                        // 2. Permite el acceso anónimo a tu login/registro web
                        .requestMatchers("/api/auth/**").permitAll()

                        // 3. (Opcional) Permite el acceso a los WebSockets
                        .requestMatchers("/ws/**").permitAll()

                        // 4. Asegura todos los demás endpoints
                        .anyRequest().authenticated()

                )// --- ¡LA CORRECCIÓN ESTÁ AQUÍ! ---
                // 5. Habilita HTTP Basic Authentication
                .httpBasic(Customizer.withDefaults()); // <-- AÑADIR ESTA LÍNEA

        // ...
        return http.build();
    }


    // --- CONFIGURACIÓN CORS ---
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Permitir solo a tu Frontend
        configuration.setAllowedOrigins(List.of(frontendUrl));

        // Permitir los métodos HTTP necesarios
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

        // Permitir headers (Authorization, Content-Type, etc.)
        configuration.setAllowedHeaders(List.of("*"));

        // Permitir enviar credenciales (cookies o auth headers)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}