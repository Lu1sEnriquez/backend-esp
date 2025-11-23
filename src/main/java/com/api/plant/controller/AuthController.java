package com.api.plant.controller;

import com.api.plant.dto.AuthRequest;
import com.api.plant.entity.AppUser;
import com.api.plant.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal; // Importante importar esto
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserDetailsService userDetailsService;

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody AuthRequest request) {
        try {
            // Validar usuario existente
            if (userDetailsService.loadUserByUsername(request.username()) != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Error: El nombre de usuario ya existe.");
            }
        } catch (Exception e) {
            // Usuario no existe, continuamos
        }

        try {
            AppUser newUser = authService.registerNewUser(
                    request.username(),
                    request.password(),
                    request.email()
            );

            // 🔥 CAMBIO IMPORTANTE: Devolvemos un JSON con los datos útiles
            return ResponseEntity.status(HttpStatus.CREATED).body(newUser);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al registrar: " + e.getMessage());
        }
    }
    /**
     * Endpoint para Login.
     * Al llegar aquí, Spring Security ya validó la contraseña (Basic Auth).
     * Ahora recuperamos el usuario de la DB para devolver su ID y Email.
     */
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(Principal principal) {
        // 'principal' contiene el usuario que acaba de pasar la autenticación Basic
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No autenticado");
        }

        String username = principal.getName();
        Optional<AppUser> userOpt = authService.findUserByUsername(username);

        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();

            // Devolvemos el objeto JSON con los datos útiles para el Frontend
            return ResponseEntity.ok(user);
        }

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado en la base de datos");
    }
}