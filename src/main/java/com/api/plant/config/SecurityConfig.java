package com.api.plant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    // En SecurityConfig.java

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // --- ¡LA CORRECCIÓN ESTÁ AQUÍ! ---
                        // 1. Permite el acceso anónimo a los endpoints de autenticación de Mosquitto
                        // 1. Permite el acceso anónimo a los endpoints de autenticación de Mosquitto
                        .requestMatchers("/api/mqtt/**").permitAll()

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
}