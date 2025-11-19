package com.api.plant.controller;

import com.api.plant.dto.DiscoveredDeviceDTO;
import com.api.plant.dto.device.DeviceRegistrationRequest;
import com.api.plant.dto.device.DeviceRegistrationResponse;
import com.api.plant.dto.device.DeviceSyncRequest;
import com.api.plant.dto.device.PlantDeviceUpdateDto;
import com.api.plant.dto.command.GenericCommandPayload;
import com.api.plant.entity.AppUser;
import com.api.plant.entity.PlantDevice;
import com.api.plant.repository.AppUserRepository;
import com.api.plant.service.ActuatorService;
import com.api.plant.service.DeviceService;
import com.api.plant.service.UserDetailsServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/devices")
public class PlantDeviceController {

    private static final Logger log = LoggerFactory.getLogger(PlantDeviceController.class);

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    // NECESITAS INYECTAR EL REPOSITORIO DE USUARIOS
    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private ActuatorService actuatorService; //


    /**
     * Endpoint para vincular un dispositivo descubierto (por MAC) a un usuario.
     * Esto dispara el envío de credenciales al ESP32.
     * Ruta: POST /api/devices/sync/{macAddress}
     */
    @PostMapping("/sync/{macAddress}")
    public ResponseEntity<?> syncDevice(
            @PathVariable String macAddress,
            @RequestBody DeviceSyncRequest syncRequest,
            Authentication authentication) {

        try {
            // Obtiene el ID del usuario autenticado (simulado por Postman con un JWT)
            String userId = getUserId(authentication.getName());

            log.info("Iniciando sincronización para MAC {} por usuario {}", macAddress, userId);

            // 1. Llama al método de sincronización en DeviceService
            PlantDevice device = deviceService.syncDeviceToUser(
                    userId,
                    macAddress,
                    syncRequest.name(),
                    syncRequest.description()
            );

            // 2. Devuelve el dispositivo ya vinculado
            return ResponseEntity.ok(device);

        } catch (Exception e) {
            log.error("Error durante la sincronización del dispositivo: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error al sincronizar: " + e.getMessage());
        }
    }




    // Helper corregido para obtener el ID de usuario de MongoDB
    private String getUserId(String username) throws Exception {
        // En lugar de usar UserDetailsService (que devuelve un objeto de seguridad genérico),
        // buscamos la entidad AppUser directamente en MongoDB para obtener el ID.
        AppUser appUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new Exception("Usuario no encontrado en la base de datos."));

        return appUser.getId(); // DEVOLVEMOS EL ID DE MONGODB (STRING)
    }


    /**
     * GET /api/devices/available
     * Obtiene todos los dispositivos que han sido descubiertos (por el MqttIngestionService)
     * pero que aún no tienen un 'ownerId' (no han sido vinculados).
     */
    @GetMapping("/available")
    public ResponseEntity<List<DiscoveredDeviceDTO>> getAvailableDevices() {
        try {
            // 1. Llamar al servicio para encontrar dispositivos sin dueño
            List<PlantDevice> availableDevices = deviceService.findAvailableDevices();

            // 2. Mapear a DTOs de forma segura
            List<DiscoveredDeviceDTO> dtos = availableDevices.stream()
                    .map(device -> new DiscoveredDeviceDTO(
                            device.getMacAddress(),
                            device.getName() // El nombre temporal (ej. "Dispositivo Desconocido")
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(dtos);

        } catch (Exception e) {
            log.error("Error al buscar dispositivos disponibles: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }


    // --- 2. GET: Listar Dispositivos ---

    /**
     * Endpoint para listar todas las plantas asociadas al usuario autenticado.
     * Ruta: GET /api/devices
     */
    @GetMapping
    public ResponseEntity<List<PlantDevice>> listUserDevices(Authentication authentication) {
        try {
            String userId = getUserId(authentication.getName());

            List<PlantDevice> devices = deviceService.getDevicesByOwner(userId);
            return ResponseEntity.ok(devices);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // --- 3. PUT: Actualizar Umbrales del Advisor ---

    /**
     * Endpoint para actualizar los umbrales de la lógica Advisor de una planta específica.
     * Ruta: PUT /api/devices/{plantId}/thresholds
     * Nota: El DTO PlantDeviceUpdateDto debería existir para recibir solo los campos actualizables.
     */
    @PutMapping("/{plantId}/thresholds")
    public ResponseEntity<?> updateDeviceThresholds(
            @PathVariable String plantId,
            @RequestBody PlantDeviceUpdateDto updateDto, // Asumimos esta clase existe.
            Authentication authentication
    ) {
        try {
            String userId = getUserId(authentication.getName());

            // Lógica de validación de propiedad (asegurar que el usuario es el dueño)
            Optional<PlantDevice> deviceOpt = deviceService.getDeviceByPlantId(plantId);

            if (deviceOpt.isEmpty() || !deviceOpt.get().getOwnerId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Dispositivo no encontrado o no autorizado.");
            }

            // Llamar al servicio para aplicar la actualización
            PlantDevice updatedDevice = deviceService.updateThresholds(plantId, updateDto);

            return ResponseEntity.ok(updatedDevice);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error al actualizar umbrales: " + e.getMessage());
        }
    }

    // --- 4. GET: Obtener detalle de un dispositivo ---

    /**
     * Endpoint para obtener los detalles de una planta por su PlantID.
     * Ruta: GET /api/devices/{plantId}
     */
    @GetMapping("/{plantId}")
    public ResponseEntity<PlantDevice> getDeviceDetails(
            @PathVariable String plantId,
            Authentication authentication
    ) {
        try {
            String userId = getUserId(authentication.getName());

            Optional<PlantDevice> deviceOpt = deviceService.getDeviceByPlantId(plantId);

            if (deviceOpt.isEmpty() || !deviceOpt.get().getOwnerId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            return ResponseEntity.ok(deviceOpt.get());

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }


    // --- 4. POST: Disparar Riego Manual (Comando Clave) ---
    /**
     * Endpoint para disparar el riego manual en una planta específica.
     * Ruta: POST /api/v1/plants/{plantId}/water
     */
    @PostMapping("/{plantId}/command")
    public ResponseEntity<String> triggerManualWatering(
            @PathVariable String plantId,
            @RequestBody(required = false) GenericCommandPayload commandPayload,
            Authentication authentication
    ) {
        try {
            String userId = getUserId(authentication.getName());

            // 1. Verificar Propiedad (Autorización)
            if (!deviceService.isUserOwnerOfPlant(userId, plantId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("No autorizado para controlar esta planta.");
            }

            // 2. Ejecutar el comando de riego (ActuatorService maneja el MQTT y el Broker)
            actuatorService.sendCommand(plantId,commandPayload);

            log.info("💧 Riego manual disparado por usuario {} para la planta {}", userId, plantId);
            return ResponseEntity.ok("Comando de riego enviado exitosamente.");

        } catch (IllegalStateException e) {
            // Captura errores si el broker está desconectado o el dispositivo no se encuentra
            log.error("Fallo de Actuación Manual (Estado): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("Error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Error al disparar riego manual:", e);
            return ResponseEntity.internalServerError().body("Error interno al procesar la solicitud.");
        }
    }
}