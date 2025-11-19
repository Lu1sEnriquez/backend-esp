package com.api.plant.controller;

import com.api.plant.dto.AuthRequest;
import com.api.plant.entity.AppUser;
import com.api.plant.entity.PlantDevice;
import com.api.plant.repository.PlantDeviceRepository;
import com.api.plant.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService; // Para el registro y cifrado

    @Autowired
    private UserDetailsService userDetailsService; // Usado para validar el login implícito (opcional)
    @Autowired
    private PlantDeviceRepository plantDeviceRepository; // Usado para validar el login implícito (opcional)

    /**
     * Endpoint para el Registro de nuevos usuarios.
     * Ruta: POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody AuthRequest request) {
        try {
            // 1. Validar que el usuario no exista
            if (userDetailsService.loadUserByUsername(request.username()) != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body("Error: El nombre de usuario ya existe.");
            }
        } catch (Exception e) {
            // Se espera que lance UsernameNotFoundException si el usuario no existe, lo cual es correcto.
        }

        try {
            // 2. Registrar (la contraseña se cifra en el servicio)
            AppUser newUser = authService.registerNewUser(request.username(), request.password());

            // Retornar solo la información segura (nunca el passwordHash)
            return ResponseEntity.status(HttpStatus.CREATED).body(
                    String.format("Usuario '%s' registrado con éxito. ID: %s", newUser.getUsername(), newUser.getId())
            );

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al registrar el usuario: " + e.getMessage());
        }
    }

    /**
     * Endpoint para el Login Implícito (Verificación de credenciales).
     * Ruta: POST /api/auth/login
     * Spring Security maneja la autenticación real (HTTP Basic) en la capa de filtro.
     * Este endpoint solo devuelve éxito si el filtro pasó.
     */
    @PostMapping("/login")
    public ResponseEntity<String> loginUser() {
        // Si la solicitud llega a este punto, significa que el filtro de HTTP Basic
        // ya autenticó al usuario exitosamente. Spring Security ya hizo su trabajo.
        return ResponseEntity.ok("Login exitoso. Las credenciales son válidas.");
    }


    // Dentro de AuthController.java o un nuevo MqttValidationController

    @GetMapping("/mqtt-validate")
    public ResponseEntity<?> validateMqttCredentials(
            @RequestParam("username") String plantId,
            @RequestParam("password") String rawPassword) {

        // Busca el PlantDevice por plantId
        Optional<PlantDevice> deviceOpt = plantDeviceRepository.findByPlantId(plantId);

        if (deviceOpt.isPresent()) {
            PlantDevice device = deviceOpt.get();

            // Compara el password en texto plano (ALMACENADO EN MONGO) con el password recibido.
            if (device.getMqttPassword().equals(rawPassword)) {
                // Éxito: Mosquitto permite la conexión
                return ResponseEntity.ok().build();
            }
        }

        // Fallo: Mosquitto rechaza la conexión
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
}