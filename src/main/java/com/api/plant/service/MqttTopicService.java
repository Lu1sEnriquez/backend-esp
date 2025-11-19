package com.api.plant.service;

import org.springframework.stereotype.Service;

/**
 * Servicio de utilidad para centralizar la generación de tópicos MQTT.
 * Esto evita "magic strings" y facilita el mantenimiento.
 */
@Service
public class MqttTopicService {

    // --- PREFIJOS CENTRALIZADOS ---
    // Prefijo principal para todos los datos y comandos de las plantas.
    private static final String PLANT_PREFIX = "planta";

    // Prefijo para tópicos de control (ej. provisioning)
    private static final String CONTROL_PREFIX = "control/provisioning";

    /**
     * Tópico de suscripción Wildcard para la ingesta de datos.
     * El MqttDiscoveryService usará esto para suscribir a los clientes.
     * (ej: "planta/#")
     */
    public String getWildcardSubscriptionTopic() {
        return String.format("%s/#", PLANT_PREFIX);
    }

    /**
     * Tópico donde el ESP32 debe publicar sus lecturas.
     * Usado por DeviceService al registrar un dispositivo.
     * (ej: "planta/PNT-123456/lecturas")
     */
    public String getDeviceDataTopic(String plantId) {
        return String.format("%s/%s/lecturas", PLANT_PREFIX, plantId);
    }

    /**
     * Tópico donde el ESP32 escucha los comandos de riego.
     * Usado por ActuatorService para enviar el comando.
     * (ej: "planta/PNT-123456/comando/riego")
     */
    public String getDeviceCommandTopic(String plantId) {
        return String.format("%s/%s/command/", PLANT_PREFIX, plantId);
    }

    /**
     * Tópico donde los nuevos dispositivos se anuncian para el provisioning.
     * (ej: "control/provisioning/discovery")
     */
    public String getDiscoveryTopic() {
        return String.format("%s/discovery", CONTROL_PREFIX);
    }

    /**
     * Tópico donde el ESP32 en modo PROVISIONING escucha su comando de configuración.
     * Utiliza la MAC como identificador específico para evitar que otros dispositivos
     * no vinculados reciban las credenciales.
     * @param macAddress La dirección MAC del dispositivo.
     * @return El tópico específico de control. (ej: "control/provisioning/device/AABBCCDD1122")
     */
    public String getProvisioningConfigTopic(String macAddress) {
        // La sintaxis debe ser: control/provisioning/device/781C3CB8A71C
        return String.format("%s/device/%s", CONTROL_PREFIX, macAddress);
    }

    /**
     * Tópico de suscripción Wildcard para la ingesta de control (provisioning).
     * El MqttDiscoveryService usará esto para suscribir a los clientes.
     * (ej: "control/#")
     */
    public String getWildcardControlTopic() {
        return String.format("%s/#", CONTROL_PREFIX);
    }
}