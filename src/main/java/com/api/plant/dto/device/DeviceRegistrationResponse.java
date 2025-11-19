package com.api.plant.dto.device;

/**
 * Record que define la respuesta al registrar un nuevo dispositivo.
 * Este es el contrato JSON para las credenciales del ESP32.
 */
public record DeviceRegistrationResponse(
        String plantId,         // El ID de la planta (MQTT Username)
        String mqttPassword,    // La contraseña en texto plano (MQTT Password)
        String topic,           // Tópico de publicación inicial (ej. planta/PNT-X/lecturas)
        String message          // Mensaje de confirmación/instrucciones
) {}