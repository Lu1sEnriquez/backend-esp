package com.api.plant.controller;

import com.api.plant.service.DeviceAuthService; // Un nuevo servicio de autenticación
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

// Este DTO mapea la solicitud del plugin go-auth
record AuthRequest(String username, String password, String clientid) {}

record AclRequest(
        String username,
        String clientid,
        String topic,
        @JsonProperty("acc") int access // <-- 2. AÑADIR ANOTACIÓN AQUÍ
) {}

@RestController
@RequestMapping("/api/mqtt") // Endpoint para el plugin
public class MqttAuthController {

    private static final Logger log = LoggerFactory.getLogger(MqttAuthController.class);
    private final DeviceAuthService deviceAuthService;

    public MqttAuthController(DeviceAuthService deviceAuthService) {
        this.deviceAuthService = deviceAuthService;
    }

    /**
     * Endpoint de Autenticación.
     * Mosquitto lo llama cuando un dispositivo intenta CONECTARSE.
     */
    @PostMapping("/auth")
    public ResponseEntity<String> authenticate(@RequestBody AuthRequest request) {

        boolean isAuthenticated = deviceAuthService.authenticateDevice(request.username(), request.password());

        if (isAuthenticated) {
            return ResponseEntity.ok().body("OK"); // HTTP 200
        } else {
            return ResponseEntity.status(401).body("Unauthorized"); // HTTP 401
        }
    }

    /**
     * Endpoint de ACL (Autorización).
     * Mosquitto lo llama cuando un dispositivo intenta PUBLICAR o SUSCRIBIRSE a un tópico.
     */
    @PostMapping("/acl")
    public ResponseEntity<String> authorize(@RequestBody AclRequest request) {
        log.info("ACCESS: "+String.valueOf(request.access()));
        // access: 1 = subscribe, 2 = publish
        boolean isAuthorized = deviceAuthService.authorizeTopic(request.username(), request.topic(), request.access());

        if (isAuthorized) {
            return ResponseEntity.ok().body("OK");
        } else {
            return ResponseEntity.status(401).body("Unauthorized");
        }
    }
}