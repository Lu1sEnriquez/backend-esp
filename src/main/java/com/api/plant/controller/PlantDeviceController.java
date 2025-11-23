package com.api.plant.controller;

import com.api.plant.dto.device.PlantDeviceUpdateDto;
import com.api.plant.dto.command.GenericCommandPayload;
import com.api.plant.entity.AppUser;
import com.api.plant.entity.PlantDevice;
import com.api.plant.repository.AppUserRepository;
import com.api.plant.service.ActuatorService;
import com.api.plant.service.DeviceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/devices")
public class PlantDeviceController {

    private static final Logger log = LoggerFactory.getLogger(PlantDeviceController.class);

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private ActuatorService actuatorService;

    /**
     * Helper para obtener el ID de usuario de MongoDB.
     */
    private String getUserId(String username) throws Exception {
        AppUser appUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new Exception("Usuario no encontrado en la base de datos."));
        return appUser.getId();
    }



    // --- 1. GET: Listar Dispositivos (VISTA GLOBAL) ---
    @GetMapping
    public ResponseEntity<List<PlantDevice>> listAllDevices(Authentication authentication) {
        try {
            // Obtenemos usuario solo para validar sesión, pero listamos todo
            String userId = getUserId(authentication.getName());

            // La lógica actual ignora el userId y devuelve todo (según requerimiento)
            List<PlantDevice> devices = deviceService.getDevicesByOwner(userId);
            return ResponseEntity.ok(devices);

        } catch (Exception e) {
            log.error("Error al listar dispositivos:", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // --- 2. PUT: Actualizar Umbrales del Advisor ---
    @PutMapping("/{plantId}/thresholds")
    public ResponseEntity<?> updateDeviceThresholds(
            @PathVariable String plantId,
            @RequestBody PlantDeviceUpdateDto updateDto,
            Authentication authentication
    ) {
        try {
            Optional<PlantDevice> deviceOpt = deviceService.getDeviceByPlantId(plantId);

            if (deviceOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Dispositivo no encontrado.");
            }

            PlantDevice updatedDevice = deviceService.updateThresholds(plantId, updateDto);
            return ResponseEntity.ok(updatedDevice);

        } catch (Exception e) {
            log.error("Error al actualizar umbrales: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Error al actualizar umbrales: " + e.getMessage());
        }
    }

    // --- 3. GET: Obtener detalle de un dispositivo ---
    @GetMapping("/{plantId}")
    public ResponseEntity<PlantDevice> getDeviceDetails(
            @PathVariable String plantId,
            Authentication authentication
    ) {
        try {
            Optional<PlantDevice> deviceOpt = deviceService.getDeviceByPlantId(plantId);

            if (deviceOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            return ResponseEntity.ok(deviceOpt.get());

        } catch (Exception e) {
            log.error("Error al obtener detalles del dispositivo:", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // --- 4. POST: Disparar Comando Manual (Riego/Luz) ---
    @PostMapping("/{plantId}/command")
    public ResponseEntity<String> triggerManualCommand(
            @PathVariable String plantId,
            @RequestBody GenericCommandPayload commandPayload,
            Authentication authentication
    ) {
        try {
            String userId = getUserId(authentication.getName());

            // 1. Verificar existencia
            Optional<PlantDevice> deviceOpt = deviceService.getDeviceByPlantId(plantId);
            if (deviceOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Planta no encontrada.");
            }

            // (Opcional) Verificar si está activa antes de enviar comando
            if (!deviceOpt.get().getIsActive()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("La planta está inactiva.");
            }

            // 2. Ejecutar el comando vía MQTT
            actuatorService.sendCommand(plantId, commandPayload);

            log.info("💧 Comando {} enviado a {} por usuario {}", commandPayload.command(), plantId, userId);
            return ResponseEntity.ok("Comando enviado exitosamente.");

        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Error de conexión MQTT: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error al disparar comando manual:", e);
            return ResponseEntity.internalServerError().body("Error interno al procesar la solicitud.");
        }
    }

    // --- 5. POST: Crear Nueva Planta ---

    // DTO interno actualizado para incluir userId (opcional, o sacarlo del contexto de seguridad)
    public record CreateDeviceRequest(String plantId, String name, String userId) {}

    @PostMapping
    public ResponseEntity<?> createDevice(@RequestBody CreateDeviceRequest request) {
        try {
            // Validamos que venga el userId
            if (request.userId() == null || request.userId().isEmpty()) {
                return ResponseEntity.badRequest().body("El userId es obligatorio");
            }

            PlantDevice newDevice = deviceService.createDevice(
                    request.plantId(),
                    request.name(),
                    request.userId() // Pasamos el ID real del usuario
            );
            return ResponseEntity.ok(newDevice);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}